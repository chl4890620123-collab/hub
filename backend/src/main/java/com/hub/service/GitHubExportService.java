package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.repository.DocumentRepository;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Write-side GitHub integration kept separate from the read-only connector.
 * A project administrator may either create a new file on an existing branch or
 * propose the file through a dedicated branch + pull request.
 */
@Service
public class GitHubExportService {
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9._/-]{1,200}");

    private final RestClient client;
    private final ObjectMapper json;
    private final ExternalOAuthTokenStore tokens;
    private final DocumentRepository documents;

    public GitHubExportService(RestClient.Builder builder, ObjectMapper json,
                               ExternalOAuthTokenStore tokens, DocumentRepository documents) {
        this.client = builder
                .baseUrl("https://api.github.com")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .build();
        this.json = json;
        this.tokens = tokens;
        this.documents = documents;
    }

    public record ExportRequest(String repository, String path, String mode, String branch,
                                String commitMessage, String prTitle) {}
    public record ExportResult(String status, String repository, String path, String branch,
                               String url, String pullRequestUrl) {}

    public ExportResult export(long projectId, long documentId, long userId, ExportRequest request) {
        if (request == null) throw new IllegalArgumentException("GitHub 저장 요청이 비어 있습니다.");
        String repository = requiredRepository(request.repository());
        String path = safePath(request.path());
        String mode = request.mode() == null ? "PR" : request.mode().trim().toUpperCase(java.util.Locale.ROOT);
        if (!mode.equals("PR") && !mode.equals("SAVE_AS")) {
            throw new IllegalArgumentException("저장 방식은 SAVE_AS 또는 PR이어야 합니다.");
        }

        String token = tokens.get(userId, "GITHUB");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GitHub 계정을 먼저 연결해 주세요.");
        }

        var meta = documents.findMeta(documentId)
                .orElseThrow(() -> new IllegalArgumentException("내보낼 문서를 찾을 수 없습니다."));
        if (meta.projectId() != projectId) throw new IllegalArgumentException("현재 프로젝트의 문서가 아닙니다.");
        if (meta.archived() || meta.sourceDeleted()) throw new IllegalArgumentException("보관되거나 삭제된 문서는 내보낼 수 없습니다.");
        var latest = documents.latestVersion(documentId)
                .orElseThrow(() -> new IllegalArgumentException("내보낼 문서 버전을 찾을 수 없습니다."));
        if (documents.isVersionContentPurged(latest.id())) {
            throw new IllegalArgumentException("본문 보존기간이 지난 문서는 원문을 복원한 뒤 내보내 주세요.");
        }
        Map<String, Object> detail = documents.versionDetail(latest.id());
        String content = value(detail, "full_text");
        if (content.isBlank()) throw new IllegalArgumentException("내보낼 문서 본문이 비어 있습니다.");

        String defaultBranch = repository(repository, token).path("default_branch").asText("main");
        String baseBranch = safeBranch(blank(request.branch()) ? defaultBranch : request.branch());
        String message = blank(request.commitMessage())
                ? "docs: export " + meta.originalName() + " from Hub"
                : request.commitMessage().trim();

        if (mode.equals("SAVE_AS")) {
            JsonNode saved = putContents(repository, path, baseBranch, content, message, token);
            return new ExportResult("SAVED", repository, path, baseBranch,
                    saved.path("content").path("html_url").asText(""), null);
        }

        String baseSha = branch(repository, baseBranch, token).path("commit").path("sha").asText("");
        if (baseSha.isBlank()) throw new IllegalStateException("GitHub 기준 브랜치의 최신 커밋을 확인하지 못했습니다.");
        String proposalBranch = "hub-export-" + documentId + "-" + Instant.now().toEpochMilli();
        post("/repos/" + repository + "/git/refs",
                Map.of("ref", "refs/heads/" + proposalBranch, "sha", baseSha), token);
        JsonNode saved = putContents(repository, path, proposalBranch, content, message, token);

        String title = blank(request.prTitle()) ? "Hub: " + meta.originalName() + " 반영" : request.prTitle().trim();
        Map<String, Object> pull = new LinkedHashMap<>();
        pull.put("title", title);
        pull.put("head", proposalBranch);
        pull.put("base", baseBranch);
        pull.put("body", "Hub 프로젝트에서 검토 후 보낸 문서 변경 제안입니다.");
        JsonNode pr = post("/repos/" + repository + "/pulls", pull, token);
        return new ExportResult("PULL_REQUEST_CREATED", repository, path, proposalBranch,
                saved.path("content").path("html_url").asText(""), pr.path("html_url").asText(""));
    }

    private JsonNode repository(String repository, String token) {
        return get("/repos/" + repository, token);
    }

    private JsonNode branch(String repository, String branch, String token) {
        return get("/repos/" + repository + "/branches/" + UriUtils.encodePath(branch, StandardCharsets.UTF_8), token);
    }

    private JsonNode putContents(String repository, String path, String branch, String content,
                                 String message, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        body.put("branch", branch);
        return put("/repos/" + repository + "/contents/" + UriUtils.encodePath(path, StandardCharsets.UTF_8), body, token);
    }

    private JsonNode get(String path, String token) {
        try {
            String body = client.get().uri(path)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().body(String.class);
            return json.readTree(body == null ? "{}" : body);
        } catch (HttpClientErrorException e) {
            throw githubError(e);
        } catch (RestClientException e) {
            throw new IllegalStateException("GitHub에 연결하지 못했습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("GitHub 응답을 처리하지 못했습니다.", e);
        }
    }

    private JsonNode post(String path, Object body, String token) {
        try {
            String response = client.post().uri(path)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(body))
                    .retrieve().body(String.class);
            return json.readTree(response == null ? "{}" : response);
        } catch (HttpClientErrorException e) {
            throw githubError(e);
        } catch (RestClientException e) {
            throw new IllegalStateException("GitHub에 변경사항을 보내지 못했습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("GitHub 요청을 처리하지 못했습니다.", e);
        }
    }

    private JsonNode put(String path, Object body, String token) {
        try {
            String response = client.put().uri(path)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(body))
                    .retrieve().body(String.class);
            return json.readTree(response == null ? "{}" : response);
        } catch (HttpClientErrorException e) {
            throw githubError(e);
        } catch (RestClientException e) {
            throw new IllegalStateException("GitHub에 파일을 저장하지 못했습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("GitHub 저장 응답을 처리하지 못했습니다.", e);
        }
    }

    private static IllegalStateException githubError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        String message = switch (status) {
            case 401 -> "GitHub 연결이 만료되었습니다. 계정을 다시 연결해 주세요.";
            case 403 -> "이 GitHub 저장소에 쓰기 권한이 없습니다.";
            case 404 -> "GitHub 저장소 또는 브랜치를 찾을 수 없습니다.";
            case 409 -> "GitHub 저장소 상태가 변경되어 저장하지 못했습니다. 다시 시도해 주세요.";
            case 422 -> "같은 경로의 파일이 이미 있거나 GitHub가 요청을 거부했습니다. 다른 이름이나 경로를 사용해 주세요.";
            default -> "GitHub 요청이 실패했습니다. (HTTP " + status + ")";
        };
        return new IllegalStateException(message, e);
    }

    private static String requiredRepository(String value) {
        String repository = value == null ? "" : value.trim();
        if (!REPOSITORY.matcher(repository).matches()) {
            throw new IllegalArgumentException("GitHub 저장소를 목록에서 다시 선택해 주세요.");
        }
        return repository;
    }

    private static String safePath(String value) {
        String path = value == null ? "" : value.trim().replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.endsWith("/") || path.contains("../") || path.equals("..") || path.length() > 500) {
            throw new IllegalArgumentException("저장 경로를 다시 확인해 주세요.");
        }
        return path;
    }

    private static String safeBranch(String value) {
        String branch = value == null ? "" : value.trim();
        if (!BRANCH.matcher(branch).matches() || branch.startsWith("/") || branch.endsWith("/") || branch.contains("..") || branch.contains("//")) {
            throw new IllegalArgumentException("GitHub 브랜치 이름을 다시 확인해 주세요.");
        }
        return branch;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(java.util.Locale.ROOT));
        return value == null ? "" : String.valueOf(value);
    }
}
