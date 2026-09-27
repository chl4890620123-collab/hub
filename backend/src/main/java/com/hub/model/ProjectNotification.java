package com.hub.model;

public record ProjectNotification(
        String id,
        String category,
        String source,
        String severity,
        String title,
        String detail,
        String occurredAt,
        String url
) {}
