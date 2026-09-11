package com.hub.model;

import java.time.LocalDateTime;

public record TimelineEvent(long id, long projectId, String eventType, String title, String description,
                            LocalDateTime happenedAt, String sourceType, Long sourceId) {}
