package com.hub.service;

import com.hub.repository.TodoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Moves old completed work into the normal restorable trash instead of physically deleting it.
 * This keeps active lists bounded without bypassing the user's delete/restore model. Permanent
 * deletion remains an explicit trash action, and nothing still open is ever eligible.
 */
@Service
public class TodoRetentionService {
    private static final Logger log = LoggerFactory.getLogger(TodoRetentionService.class);
    private final TodoRepository todos;
    private final boolean enabled;
    private final int retentionMonths;

    public TodoRetentionService(TodoRepository todos,
                                @Value("${hub.todo-retention-enabled:true}") boolean enabled,
                                @Value("${hub.todo-retention-months:12}") int retentionMonths) {
        this.todos = todos;
        this.enabled = enabled;
        this.retentionMonths = retentionMonths;
    }

    @Scheduled(fixedDelayString = "${hub.todo-retention-interval-ms:86400000}",
            initialDelayString = "${hub.todo-retention-initial-delay-ms:300000}")
    public void purgeOldCompletedTodos() {
        if (!enabled) return;
        LocalDate cutoff = LocalDate.now().minusMonths(Math.max(1, retentionMonths));
        int moved = todos.moveCompletedToTrashOlderThan(cutoff);
        if (moved > 0) log.info("Todo retention cleanup moved {} completed todo(s) to trash; due before {}", moved, cutoff);
    }
}
