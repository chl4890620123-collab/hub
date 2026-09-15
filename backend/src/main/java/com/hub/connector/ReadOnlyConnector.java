package com.hub.connector;

import java.util.List;

public interface ReadOnlyConnector {
    String type();
    List<ExternalContent> fetch(String scope, String accessToken);

    /**
     * What this account can import from. The connector screen offers exactly these, because a
     * hand-typed scope is how a project ended up syncing "노트북" and failing on every run.
     */
    default List<ConnectorTarget> targets(String accessToken) {
        return List.of();
    }
}
