// Read-only connector service uses one server-managed credential source and idempotent snapshot imports.
package com.hub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.config.HubProperties;
import com.hub.connector.ExternalContent;
import com.hub.connector.ReadOnlyConnector;
import com.hub.model.User;
import com.hub.repository.ConnectorPolicyRepository;
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
    private final ConnectorPolicyRepository policy;
    private final ProjectAccessService projectAccess;
    private final SensitiveDataMaskingService piiMasking;

    public ConnectorService(List<ReadOnlyConnector> adapters,
                            ConnectorRepository repository,
                            DocumentService documents,
                            TimelineRepository timeline,
                            ObjectMapper json,
                            HubProperties props,
                            GoogleAccessTokenProvider googleTokens, ExternalOAuthService externalOAuth,
                            ConnectorPolicyRepository policy, ProjectAccessService projectAccess,
                            SensitiveDataMaskingService piiMasking) {
        adapters.forEach(adapter -> this.adapters.put(adapter.type(), adapter));
        this.repository = repository;
        this.documents = documents;
        this.timeline = timeline;
        this.json = json;
        this.props = props;
        this.googleTokens = googleTokens;
        this.externalOAuth = externalOAuth;
        this.policy = policy;
        this.projectAccess = projectAccess;
        this.piiMasking = piiMasking;
    }

    /**
     * Lists what the caller's own credentials can reach, plus whether those credentials came from this
     * account's own link or from the server configuration, so the screen can say which is in use.
     * Personal on purpose: a teammate's Drive/Slack/GitHub account can see different files/channels/
     * repos than this account can, so there is no single project-wide answer to "what's available".
     */
    public java.util.Map<String,Object> targets(String type, User user) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        ReadOnlyConnector adapter = adapters.get(normalizedType);
        if (adapter == null) throw new IllegalArgumentException("지원하지 않는 연결 서비스입니다.");
        if (!policy.isEnabled(normalizedType)) throw new IllegalArgumentException(connectorName(normalizedType) + "는 관리자가 사용을 막아 두었습니다.");
        String token = resolveToken(normalizedType, user);
        if (token == null || token.isBlank())
            return java.util.Map.of("connected", false, "linkedByUser", false, "targets", java.util.List.of());
        boolean linked = linkedByUser(normalizedType, user);
        String account = linked ? accountLabel(normalizedType, user) : null;
        java.util.Map<String,Object> result = new java.util.LinkedHashMap<>();
        result.put("connected", true);
        result.put("linkedByUser", linked);
        result.put("account", account);
        result.put("targets", adapter.targets(token));
        return result;
    }

    /** Name of the external account behind this account's own link, when it made one. */
    public String accountLabel(String type, User user) {
        if ("GOOGLE_DRIVE".equals(type)) return googleTokens.accountLabel(user.id());
        return externalOAuth.accountLabel(user.id(), type);
    }

    /** Drops this account's own link so the next import falls back to the server credentials. */
    public void disconnect(String type, User user) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("GOOGLE_DRIVE".equals(normalized)) googleTokens.disconnect(user.id());
        else externalOAuth.disconnect(user.id(), normalized);
    }

    /** Whether the credential in use was granted by this account rather than read from the server config. */
    public boolean linkedByUser(String type, User user) {
        if ("GOOGLE_DRIVE".equals(type)) return googleTokens.linkedByUser(user.id());
        return externalOAuth.connected(user.id(), type);
    }

    /**
     * Connector sync is read-only toward the provider. Re-importing the same external id is safe:
     * external metadata is upserted and DocumentService creates a new version only when content changed.
     * Whoever triggers the import uses their own personal credential, but the imported content always
     * lands in this project's shared document pool - that is where results from different teammates'
     * accounts combine, not in the credential itself.
     */
    public int importItems(long projectId, String type, String scope, User user) {
        // Re-check at the service boundary because this method is also called from async/auto-sync jobs.
        // A user may have been removed from the project after the HTTP request queued the work.
        projectAccess.requireAccess(projectId, user);
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        ReadOnlyConnector adapter = adapters.get(normalizedType);
        if (adapter == null) throw new IllegalArgumentException("지원하지 않는 연결 서비스입니다.");
        if (!policy.isEnabled(normalizedType)) throw new IllegalArgumentException(connectorName(normalizedType) + "는 관리자가 사용을 막아 두었습니다.");
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("가져올 범위를 입력해 주세요.");

        String cleanScope=scope.trim();
        String effectiveToken = resolveToken(normalizedType, user);
        if (effectiveToken == null || effectiveToken.isBlank()) {
            throw new IllegalArgumentException(connectorName(normalizedType) + " 계정을 먼저 연결해 주세요.");
        }
        int imported = 0;
        int skipped = 0;
        int excludedDeleted = 0;
        int excludedArchived = 0;
        Long connectorAccountId = externalOAuth.accountId(user.id(), normalizedType);
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
            String sourceIdentifier = adapter.type() + ":" + item.externalId();
            if (documents.isPermanentlyDeletedExternalSource(projectId, adapter.type(), sourceIdentifier)) {
                excludedDeleted++;
                continue;
            }
            if (documents.isArchivedExternalSource(projectId, adapter.type(), sourceIdentifier)) {
                excludedArchived++;
                continue;
            }
            repository.saveItem(
                    projectId, connectorAccountId, adapter.type(), item.externalId(), item.itemType(),
                    piiMasking.mask(normalizedTitle), piiMasking.mask(normalizedContent),
                    piiMasking.mask(normalizedAuthor), item.sourceUrl(), item.createdAt(), metadata
            );
            // A folder is a mixed bag: one binary blob nobody can read must not discard the files
            // that imported fine before it. The item is skipped and reported in the sync status.
            try {
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
                } else {
                    skipped++;
                }
            } catch (RuntimeException itemFailure) {
                skipped++;
            }
        }
        java.util.List<String> notices = new java.util.ArrayList<>();
        if (skipped > 0) notices.add("읽을 수 없는 파일 " + skipped + "건은 건너뛰었습니다.");
        if (excludedArchived > 0) notices.add("보관 중인 자료 " + excludedArchived + "건은 가져오지 않았습니다.");
        if (excludedDeleted > 0) notices.add("영구 삭제한 자료 " + excludedDeleted + "건은 다시 가져오지 않았습니다.");
        String note = notices.isEmpty() ? null : String.join(" ", notices);
        repository.saveSyncState(projectId, normalizedType, cleanScope, user.id(), "SUCCESS", note, imported);
        timeline.append(
                projectId,
                "CONNECTOR_IMPORT",
                connectorName(normalizedType) + " 자료 가져오기",
                imported + "건의 자료를 가져왔습니다.",
                LocalDateTime.now(),
                "CONNECTOR",
                null
        );
        return imported;
        } catch (RuntimeException ex) {
            repository.saveSyncState(projectId, normalizedType, cleanScope, user.id(), "FAILED", safeMessage(ex, effectiveToken), imported);
            throw ex;
        }
    }

    public List<ConnectorRepository.SyncState> syncStates(long projectId) { return repository.listSyncStates(projectId); }

    private static String safeMessage(RuntimeException ex, String token) {
        String msg=ex.getMessage();
        if(msg==null||msg.isBlank()) return "연결 서비스 처리 중 오류가 발생했습니다.";
        if(token!=null&&!token.isBlank()) msg=msg.replace(token,"[REDACTED]");
        return msg.length()>500?msg.substring(0,500):msg;
    }

    /**
     * Every connector resolves the same way: the credential this account personally linked wins, and
     * the shared one from the server configuration is the fallback - but only when an admin turned
     * that fallback on (hub.allow-shared-connector-fallback). Without it, an unlinked user gets no
     * token at all instead of silently browsing whatever account the server happens to be configured
     * with. GitHub used to skip the personal token entirely, so a user who had signed in to GitHub
     * still browsed the server account's repositories.
     */
    private String resolveToken(String type, User user) {
        if ("GOOGLE_DRIVE".equals(type)) {
            if (!googleTokens.connected(user.id())) return null;
            return googleTokens.accessToken(user.id());
        }
        // External providers are always account-scoped: the personal link wins whenever it exists,
        // so a server token never makes one user's repository/channel/page list look like another
        // user's linked account. The shared token is only a fallback for a user who hasn't linked yet.
        if ("GITHUB".equals(type) || "SLACK".equals(type) || "NOTION".equals(type)) {
            String personal = externalOAuth.token(user.id(), type);
            if (personal != null && !personal.isBlank()) return personal;
            return props.allowSharedConnectorFallback() ? sharedToken(type) : null;
        }
        return "";
    }

    private String sharedToken(String type) {
        if ("GITHUB".equals(type)) return props.githubToken();
        if ("SLACK".equals(type)) return props.slackToken();
        if ("NOTION".equals(type)) return props.notionToken();
        return null;
    }

    private static String connectorName(String type) {
        return switch (type) {
            case "GITHUB" -> "GitHub";
            case "SLACK" -> "Slack";
            case "NOTION" -> "Notion";
            case "GOOGLE_DRIVE" -> "Google Drive";
            default -> type;
        };
    }
}
