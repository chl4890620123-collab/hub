package com.hub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.ProjectNotification;
import com.hub.repository.ConnectorRepository;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GitHubNotificationService {
    private final RestClient client;
    private final ObjectMapper json;
    private final ConnectorRepository connectors;
    private final ExternalOAuthTokenStore tokens;

    public GitHubNotificationService(RestClient.Builder builder, ObjectMapper json,
                                     ConnectorRepository connectors, ExternalOAuthTokenStore tokens) {
        this.client = builder
                .baseUrl("https://api.github.com")
                .requestFactory(HttpRequestFactories.create(Duration.ofSeconds(5), Duration.ofSeconds(30)))
                .build();
        this.json = json;
        this.connectors = connectors;
        this.tokens = tokens;
    }

    public List<ProjectNotification> workflowAlerts(long projectId, long userId) {
        String token = tokens.get(userId, "GITHUB");
        if (token == null || token.isBlank()) return List.of();

        Set<String> repositories = new LinkedHashSet<>();
        for (ConnectorRepository.SyncState state : connectors.listSyncStates(projectId)) {
            if ("GITHUB".equalsIgnoreCase(state.connectorType())
                    && state.externalScope() != null
                    && state.externalScope().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                repositories.add(state.externalScope());
            }
        }

        List<ProjectNotification> out = new ArrayList<>();
        for (String repository : repositories.stream().limit(10).toList()) {
            try {
                String response = client.get()
                        .uri("/repos/" + repository + "/actions/runs?per_page=20&status=completed")
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve().body(String.class);
                JsonNode runs = json.readTree(response == null ? "{}" : response).path("workflow_runs");
                if (!runs.isArray()) continue;
                for (JsonNode run : runs) {
                    String conclusion = run.path("conclusion").asText("");
                    if (!isAlertConclusion(conclusion)) continue;
                    String id = run.path("id").asText("");
                    String name = run.path("name").asText("GitHub Actions");
                    String display = run.path("display_title").asText("");
                    String updated = run.path("updated_at").asText(run.path("created_at").asText(""));
                    String url = run.path("html_url").asText("");
                    out.add(new ProjectNotification(
                            "github-run:" + repository + ":" + id,
                            "GIT_CI",
                            "GitHub",
                            "ERROR",
                            repository + " · " + name + " " + conclusionLabel(conclusion),
                            display,
                            updated,
                            url
                    ));
                }
            } catch (Exception e) {
                out.add(new ProjectNotification(
                        "github-check:" + repository,
                        "GIT_CI",
                        "GitHub",
                        "WARNING",
                        repository + " · CI 상태 확인 실패",
                        safeMessage(e),
                        "",
                        "https://github.com/" + repository + "/actions"
                ));
            }
        }
        return out;
    }

    private static boolean isAlertConclusion(String conclusion) {
        return switch (conclusion) {
            case "failure", "cancelled", "timed_out", "action_required", "startup_failure", "stale" -> true;
            default -> false;
        };
    }

    private static String conclusionLabel(String conclusion) {
        return switch (conclusion) {
            case "failure" -> "실패";
            case "cancelled" -> "취소";
            case "timed_out" -> "시간 초과";
            case "action_required" -> "확인 필요";
            case "startup_failure" -> "시작 실패";
            case "stale" -> "중단";
            default -> conclusion;
        };
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "GitHub 연결 상태를 확인해 주세요.";
        message = message.replaceAll("(?i)(token|secret|password)=[^\\s&]+", "$1=[REDACTED]");
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
