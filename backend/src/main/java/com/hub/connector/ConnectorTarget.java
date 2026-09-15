package com.hub.connector;

/**
 * One thing an operator may import from: a repository, a channel, a page, a folder.
 * `id` is what gets stored as the sync scope, so it is never typed by hand.
 */
public record ConnectorTarget(String id, String name, String description, String url) {
    public ConnectorTarget(String id, String name, String description) {
        this(id, name, description, "");
    }
}
