package com.hub.model;

import java.time.LocalDateTime;
import java.util.Map;

public record SpreadsheetRow(long id, long fileId, int position, Map<String, String> cells, LocalDateTime updatedAt) {}
