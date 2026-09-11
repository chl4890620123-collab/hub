// Read-only connector service uses one server-managed credential source and idempotent snapshot imports.
package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.connector.ExternalContent;
import com.hub.connector.ReadOnlyConnector;
import com.hub.model.User;
import com.hub.repository.ConnectorRepository;
import com.hub.repository.TimelineRepository;
import com.hub.util.UnicodeText;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ConnectorService {
    private final Map<String, ReadOnlyConnector> adapters = new HashMap<>();
    private final ConnectorRepository repository;
    private final DocumentService documents;
    private final TimelineRepository timeline;
    private final ObjectMapper json;
    private final HubProperties props;
    private final GoogleAccessTokenProvider googleTokens;
    private final ExternalOAuthService externalOAuth;

    public ConnectorService(List<ReadOnlyConnector> adapters,
                            ConnectorRepository repository,
                            DocumentService documents,
                            TimelineRepository timeline,
                            ObjectMapper json,
                            HubProperties props,
                            GoogleAccessTokenProvider googleTokens, ExternalOAuthService externalOAuth) {
        adapters.forEach(adapter -> this.adapters.put(adapter.type(), adapter));
        this.repository = repository;
        this.documents = documents;
        this.timeline = timeline;
        this.json = json;
        this.props = props;
        this.googleTokens = googleTokens;
        this.externalOAuth = externalOAuth;
    }

    /**
     * Connector sync is read-only toward the provider. Re-importing the same external id is safe:
     * external metadata is upserted and DocumentService creates a new version only when content changed.
     */
    public int importItems(long projectId, String type, String scope, User user) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        ReadOnlyConnector adapter = adapters.get(normalizedType);
        if (adapter == null) throw new IllegalArgumentException("Unsupported connector: " + type);
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("Connector scope is required");

        String cleanScope=scope.trim();
        String effectiveToken = resolveToken(normalizedType, user);
        int imported = 0;
        try {
        for (ExternalContent item : adapter.fetch(cleanScope, effectiveToken)) {
            String metadata;
            try {
                metadata = json.writeValueAsString(item.metadata());
            } catch (Exception e) {
                metadata = "{}";
            }

            String normalizedTitle = UnicodeText.nfcNullable(item.title());
            String normalizedContent = UnicodeText.nfcNullable(item.content());
            String normalizedAuthor = UnicodeText.nfcNullable(item.author());
            repository.saveItem(
                    projectId, adapter.type(), item.externalId(), item.itemType(), normalizedTitle, normalizedContent,
                    normalizedAuthor, item.sourceUrl(), item.createdAt(), metadata
            );
            String sourceIdentifier = adapter.type() + ":" + item.externalId();
            if (item.hasBinary()) {
                documents.importExternalFile(
                        projectId, adapter.type(), sourceIdentifier, normalizedTitle, item.contentType(),
                        item.binaryContent(), user
                );
                imported++;
            } else if (normalizedContent != null && !normalizedContent.isBlank()) {
                documents.importExternalText(
                        projectId, adapter.type(), sourceIdentifier, normalizedTitle, normalizedContent, user
                );
                imported++;
            }
        }
        repository.saveSyncState(projectId, normalizedType, cleanScope, "SUCCESS", null, imported);
        timeline.append(
                projectId,
                "CONNECTOR_IMPORT",
                normalizedType + " import",
                "Imported/synced " + imported + " items from " + cleanScope,
                LocalDateTime.now(),
                "CONNECTOR",
                null
        );
        return imported;
        } catch (RuntimeException ex) {
            repository.saveSyncState(projectId, normalizedType, cleanScope, "FAILED", safeMessage(ex, effectiveToken), imported);
            throw ex;
        }
    }

    public List<ConnectorRepository.SyncState> syncStates(long projectId) { return repository.listSyncStates(projectId); }

    private static String safeMessage(RuntimeException ex, String token) {
        String msg=ex.getMessage();
        if(msg==null||msg.isBlank()) return ex.getClass().getSimpleName();
        if(token!=null&&!token.isBlank()) msg=msg.replace(token,"[REDACTED]");
        return msg.length()>500?msg.substring(0,500):msg;
    }

    private String resolveToken(String type, User user) {
        if ("GITHUB".equals(type)) return props.githubToken();
        if ("GOOGLE_DRIVE".equals(type)) return googleTokens.accessToken(user.id());
        if ("SLACK".equals(type)) return first(externalOAuth.token(user.id(), type), props.slackToken());
        if ("NOTION".equals(type)) return first(externalOAuth.token(user.id(), type), props.notionToken());
        return "";
    }

    private static String first(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
