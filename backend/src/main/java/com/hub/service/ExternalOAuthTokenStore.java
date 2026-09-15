package com.hub.service;

import com.hub.repository.ConnectorAccountRepository;
import org.springframework.stereotype.Service;

/**
 * Slack/Notion/GitHub OAuth tokens, keyed by the account that authorised them - personal, because
 * each person's own account can see different channels/repos/pages than a teammate's. Backed by the
 * database so a restart does not silently disconnect everyone who had linked a service.
 */
@Service
public class ExternalOAuthTokenStore {
    private final ConnectorAccountRepository accounts;

    public ExternalOAuthTokenStore(ConnectorAccountRepository accounts) { this.accounts = accounts; }

    public void put(long userId, Long projectId, String type, String token) { put(userId, projectId, type, token, null); }

    public void put(long userId, Long projectId, String type, String token, String accountLabel) {
        accounts.save(userId, projectId, type, token, null, null, accountLabel);
    }

    public String accountLabel(long userId, String type) { return accounts.accountLabel(userId, type); }

    public Long accountId(long userId, String type) { return accounts.id(userId, type); }

    public String get(long userId, String type) {
        return accounts.find(userId, type).map(ConnectorAccountRepository.Credential::accessToken).orElse(null);
    }

    public boolean connected(long userId, String type) { return accounts.connected(userId, type); }

    public void remove(long userId, String type) { accounts.disconnect(userId, type); }
}
