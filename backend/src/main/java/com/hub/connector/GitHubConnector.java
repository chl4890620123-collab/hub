package com.hub.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class GitHubConnector implements ReadOnlyConnector {
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    // 100/page - caps a runaway account at 1000 repos rather than looping forever.
    private static final int MAX_TARGET_PAGES = 10;

    private final RestClient client;
    private final ObjectMapper json;

    public GitHubConnector(RestClient.Builder builder, ObjectMapper json) {
        this.client = builder
                .baseUrl("https://api.github.com")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(60)))
                .build();
        this.json = json;
    }

    @Override
    public String type() {
        return "GITHUB";
    }

    @Override
    public List<ExternalContent> fetch(String repository, String token) {
        String scope = repository == null ? "" : repository.trim();
        if (!REPOSITORY.matcher(scope).matches()) {
            throw new IllegalArgumentException("GitHub 저장소를 목록에서 다시 선택해 주세요.");
        }

        List<ExternalContent> out = new ArrayList<>();
        JsonNode commits = get("/repos/" + scope + "/commits?per_page=30", token);
        if (commits.isArray()) {
            for (JsonNode commitNode : commits) {
                String sha = commitNode.path("sha").asText();
                JsonNode commit = commitNode.path("commit");
                String author = commit.path("author").path("name").asText("");
                String date = commit.path("author").path("date").asText("");
                String message = commit.path("message").asText("Commit");
                out.add(new ExternalContent(
                        sha,
                        "GIT_COMMIT",
                        firstLine(message),
                        message,
                        null,
                        "text/plain",
                        author,
                        commitNode.path("html_url").asText(""),
                        ConnectorSupport.date(date),
                        Map.of("sha", sha, "repository", scope, "location", scope + " / Commits / " + sha.substring(0, Math.min(7, sha.length())))
                ));
            }
        }

        JsonNode issues = get("/repos/" + scope + "/issues?state=all&per_page=30", token);
        if (issues.isArray()) {
            for (JsonNode issue : issues) {
                String kind = issue.has("pull_request") ? "GIT_PR" : "GIT_ISSUE";
                String id = issue.path("number").asText();
                out.add(new ExternalContent(
                        kind + ":" + id,
                        kind,
                        issue.path("title").asText(),
                        issue.path("body").asText(""),
                        null,
                        "text/plain",
                        issue.path("user").path("login").asText(""),
                        issue.path("html_url").asText(""),
                        ConnectorSupport.date(issue.path("created_at").asText("")),
                        Map.of("state", issue.path("state").asText(""), "repository", scope, "number", id, "location", scope + ("GIT_PR".equals(kind) ? " / Pull requests #" : " / Issues #") + id)
                ));
            }
        }
        return out;
    }

    private JsonNode get(String path, String token) {
        return ConnectorSupport.json(json, getEntity(path, token).getBody(), "GitHub 응답을 처리하지 못했습니다.");
    }

    private ResponseEntity<String> getEntity(String uriOrPath, String token) {
        try {
            return client.get()
                    .uri(uriOrPath)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .headers(headers -> {
                        if (token != null && !token.isBlank()) headers.setBearerAuth(token);
                    })
                    .retrieve()
                    .toEntity(String.class);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new IllegalStateException("GitHub 연결이 만료되었거나 토큰이 유효하지 않습니다. 다시 연결해 주세요.", e);
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException("GitHub 접근 권한이 없거나 요청이 제한되었습니다. 토큰 권한을 확인해 주세요.", e);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new IllegalStateException("GitHub에서 해당 저장소를 찾을 수 없습니다. 목록을 다시 불러온 뒤 선택해 주세요.", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("GitHub 자료를 가져오지 못했습니다.", e);
        }
    }

    /** Parses the RFC 5988 Link header GitHub paginates with, returning the rel="next" URL or null. */
    private static String nextLink(HttpHeaders headers) {
        String linkHeader = headers.getFirst(HttpHeaders.LINK);
        if (linkHeader == null || linkHeader.isBlank()) return null;
        for (String part : linkHeader.split(",")) {
            String[] pieces = part.split(";");
            if (pieces.length < 2) continue;
            String url = pieces[0].trim();
            if (!url.startsWith("<") || !url.endsWith(">")) continue;
            for (int i = 1; i < pieces.length; i++) {
                if (pieces[i].trim().equals("rel=\"next\"")) return url.substring(1, url.length() - 1);
            }
        }
        return null;
    }


    private static String firstLine(String value) {
        if (value == null || value.isBlank()) return "커밋";
        String first = value.lines().findFirst().orElse("커밋");
        return first.length() <= 500 ? first : first.substring(0, 500);
    }

    /** Repositories the token can read, newest activity first. Follows GitHub's Link-header
     * pagination instead of stopping at the first page, or an account with >100 repos silently
     * lost everything past it with no error and no way to reach the rest. */
    @Override
    public List<ConnectorTarget> targets(String token) {
        List<ConnectorTarget> out = new ArrayList<>();
        String uri = "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member";
        for (int page = 0; uri != null && page < MAX_TARGET_PAGES; page++) {
            ResponseEntity<String> response = getEntity(uri, token);
            JsonNode repos = ConnectorSupport.json(json, response.getBody(), "GitHub 응답을 처리하지 못했습니다.");
            if (repos.isArray()) {
                for (JsonNode repo : repos) {
                    String full = repo.path("full_name").asText("");
                    if (full.isBlank()) continue;
                    String description = repo.path("private").asBoolean(false) ? "비공개 저장소" : "공개 저장소";
                    out.add(new ConnectorTarget(full, full, description, "https://github.com/" + full));
                }
            }
            uri = nextLink(response.getHeaders());
        }
        return out;
    }
}
