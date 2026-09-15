package com.hub.service;

import com.hub.repository.TodoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Keeps the todo table from growing forever: work that is DONE and long past its due date is removed
 * so DB size/index cost stays bounded as a project accumulates years of history. This purges only the
 * todo row itself - documents, meeting recordings, and evidence content are never touched, and nothing
 * still open (any status other than DONE, or with no confirmed due date) is ever eligible.
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
        int removed = todos.purgeCompletedOlderThan(cutoff);
        if (removed > 0) log.info("Todo retention cleanup removed {} completed todo(s) due before {}", removed, cutoff);
    }
}
