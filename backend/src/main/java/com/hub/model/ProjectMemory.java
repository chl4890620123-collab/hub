package com.hub.model;

import java.time.LocalDateTime;

public record ProjectMemory(long id, long projectId, String memoryKey, String memoryValue, String memoryType,
                            String reviewStatus, String sourceType, Long sourceId, LocalDateTime updatedAt) {}
