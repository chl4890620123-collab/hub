// TODO read model separates review, execution, assignment-health, and duplicate-review state.
package com.hub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * assigneeId is an official assignment only after ADMIN confirmation.
 * AI suggestions never grant task-update permission.
 * assignmentStatus protects work when a user leaves or moves projects.
 */
public record TodoItem(
        long id,
        long projectId,
        String title,
        String description,
        Long assigneeId,
        String assigneeText,
        Long assigneeSuggestionId,
        String assigneeSuggestionText,
        LocalDate dueDate,
        LocalDate dueDateSuggestion,
        String confidence,
        String reviewStatus,
        String taskStatus,
        String assignmentStatus,
        Long possibleDuplicateOfId,
        String duplicateReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String googleCalendarEventId
) {}
