package com.hub.service;

import com.hub.model.User;
import com.hub.repository.ConnectorRepository;
import com.hub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Import today only runs when someone clicks "자료 가져오기" (ConnectorController.importItems). This
 * replays every scope that has succeeded at least once, using the same personal credential that
 * authorized it, so a project's search corpus does not go stale just because nobody remembered to
 * click the button again. Re-importing is safe: ConnectorService upserts by external id and only
 * creates a new document version when content actually changed.
 */
@Service
public class ConnectorAutoSyncService {
    private static final Logger log = LoggerFactory.getLogger(ConnectorAutoSyncService.class);
    private final ConnectorRepository syncStates;
    private final UserRepository users;
    private final ConnectorService connectorService;
    private final boolean enabled;

    public ConnectorAutoSyncService(ConnectorRepository syncStates,
                                    UserRepository users,
                                    ConnectorService connectorService,
                                    @Value("${hub.connector-auto-sync-enabled:true}") boolean enabled) {
        this.syncStates = syncStates;
        this.users = users;
        this.connectorService = connectorService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${hub.connector-auto-sync-interval-ms:1800000}",
            initialDelayString = "${hub.connector-auto-sync-initial-delay-ms:120000}")
    public void syncAll() {
        if (!enabled) return;
        for (ConnectorRepository.SyncScope scope : syncStates.allKnownScopes()) {
            resync(scope);
        }
    }

    /**
     * Replay the scope only as the exact user who originally imported it. ConnectorService performs
     * a fresh project-access check, so a removed/suspended/withdrawn user cannot keep importing in the
     * background and another teammate's credential is never substituted.
     */
    private void resync(ConnectorRepository.SyncScope scope) {
        User user = users.findById(scope.ownerUserId()).filter(User::active).orElse(null);
        if (user == null) return;
        try {
            connectorService.importItems(scope.projectId(), scope.connectorType(), scope.externalScope(), user);
        } catch (org.springframework.security.access.AccessDeniedException noLongerMember) {
            log.info("Connector auto-sync skipped for project {} {} '{}': owner {} no longer has project access",
                    scope.projectId(), scope.connectorType(), scope.externalScope(), scope.ownerUserId());
        } catch (RuntimeException failure) {
            log.warn("Connector auto-sync failed for project {} {} '{}': {}",
                    scope.projectId(), scope.connectorType(), scope.externalScope(), failure.getMessage());
        }
    }
}
