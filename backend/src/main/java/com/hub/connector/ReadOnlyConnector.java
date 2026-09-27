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

    /**
     * Provider-side pagination for browse screens. Legacy connectors may still expose targets()
     * only; the default implementation slices that list so callers have one API shape.
     */
    default ConnectorTargetPage targetsPage(String accessToken, String cursor, int pageSize) {
        List<ConnectorTarget> all = targets(accessToken);
        int size = Math.max(1, Math.min(pageSize, 100));
        int offset;
        try {
            offset = cursor == null || cursor.isBlank() ? 0 : Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException ignored) {
            offset = 0;
        }
        if (offset >= all.size()) return ConnectorTargetPage.empty();
        int end = Math.min(all.size(), offset + size);
        String next = end < all.size() ? String.valueOf(end) : "";
        return new ConnectorTargetPage(all.subList(offset, end), next, !next.isBlank());
    }
}
