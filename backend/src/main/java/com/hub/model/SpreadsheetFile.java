package com.hub.model;

import java.time.LocalDateTime;
import java.util.List;

public record SpreadsheetFile(
        long id,
        long projectId,
        String name,
        long ownerId,
        List<SpreadsheetColumn> columns,
        boolean passwordProtected,
        String passwordHint,
        int rowCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
