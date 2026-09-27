package com.hub.connector;

import java.util.List;

/**
 * One provider-side page of targets. nextCursor is opaque to the browser and is only
 * returned to the same connector on the next request.
 */
public record ConnectorTargetPage(
        List<ConnectorTarget> targets,
        String nextCursor,
        boolean hasMore
) {
    public ConnectorTargetPage {
        targets = targets == null ? List.of() : List.copyOf(targets);
        nextCursor = nextCursor == null ? "" : nextCursor;
    }

    public static ConnectorTargetPage empty() {
        return new ConnectorTargetPage(List.of(), "", false);
    }
}
