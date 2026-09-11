package com.hub.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.util.HttpRequestFactories;
import org.springframework.http.HttpHeaders;
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
            throw new IllegalArgumentException("GitHub scope must be owner/repository");
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
        try {
            String body = client.get()
                    .uri(path)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .headers(headers -> {
                        if (token != null && !token.isBlank()) headers.setBearerAuth(token);
                    })
                    .retrieve()
                    .body(String.class);
            return ConnectorSupport.json(json, body, "Invalid GitHub response");
        } catch (RestClientException e) {
            throw new IllegalStateException("GitHub API request failed", e);
        }
    }


    private static String firstLine(String value) {
        if (value == null || value.isBlank()) return "Commit";
        String first = value.lines().findFirst().orElse("Commit");
        return first.length() <= 500 ? first : first.substring(0, 500);
    }
}
