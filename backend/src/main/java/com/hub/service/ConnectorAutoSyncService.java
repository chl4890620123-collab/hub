package com.hub.service;

import com.hub.model.User;
import com.hub.repository.ConnectorAccountRepository;
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
    private final ConnectorAccountRepository accounts;
    private final UserRepository users;
    private final ConnectorService connectorService;
    private final boolean enabled;

    public ConnectorAutoSyncService(ConnectorRepository syncStates,
                                    ConnectorAccountRepository accounts,
                                    UserRepository users,
                                    ConnectorService connectorService,
                                    @Value("${hub.connector-auto-sync-enabled:true}") boolean enabled) {
        this.syncStates = syncStates;
        this.accounts = accounts;
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
     * No linked account left for this project+connector means the person who originally imported it
     * has since disconnected; skipped rather than falling back to a shared token, so the corpus never
     * silently starts filling with a different account's content than whoever set the scope up chose.
     */
    private void resync(ConnectorRepository.SyncScope scope) {
        Long userId = accounts.anyConnectedUserId(scope.projectId(), scope.connectorType()).orElse(null);
        if (userId == null) return;
        User user = users.findById(userId).filter(User::active).orElse(null);
        if (user == null) return;
        try {
            connectorService.importItems(scope.projectId(), scope.connectorType(), scope.externalScope(), user);
        } catch (RuntimeException failure) {
            log.warn("Connector auto-sync failed for project {} {} '{}': {}",
                    scope.projectId(), scope.connectorType(), scope.externalScope(), failure.getMessage());
        }
    }
}
