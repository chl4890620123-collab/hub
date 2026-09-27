// API read model for idempotent background AI/STT work used by the web application.
package com.hub.model;

import java.time.LocalDateTime;

public record ProcessingJob(
        long id,
        long projectId,
        String jobType,
        String targetType,
        Long targetId,
        Long requesterUserId,
        String status,
        int progress,
        String errorCode,
        String errorMessage,
        String resultJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
