package com.hub.controller;

import com.hub.model.TodoItem;
import com.hub.model.User;
import com.hub.repository.EvidenceRepository;
import com.hub.repository.TodoRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import com.hub.service.TodoService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class TodoController {
    private static final List<String> TASK_STATUSES = List.of("TODO", "IN_PROGRESS", "DONE", "BLOCKED");

    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final TodoService todoService;
    private final TodoRepository todos;
    private final EvidenceRepository evidence;

    public TodoController(CurrentUserService currentUser,
                          ProjectAccessService projectAccess,
                          TodoService todoService,
                          TodoRepository todos,
                          EvidenceRepository evidence) {
        this.currentUser = currentUser;
        this.projectAccess = projectAccess;
        this.todoService = todoService;
        this.todos = todos;
        this.evidence = evidence;
    }

    @GetMapping("/api/projects/{projectId}/todos")
    public List<TodoItem> month(@PathVariable long projectId,
                                @RequestParam int year,
                                @RequestParam int month,
                                Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return todoService.month(projectId, year, month);
    }

    @GetMapping("/api/projects/{projectId}/todos/undated")
    public List<TodoItem> undated(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return todoService.undated(projectId);
    }

    @GetMapping("/api/projects/{projectId}/review/todos")
    public List<TodoItem> pending(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAdmin(projectId, user);
        return todoService.pending(projectId);
    }

    @GetMapping("/api/todos/{todoId}/evidence")
    public List<EvidenceRepository.EvidenceView> evidence(@PathVariable long todoId,
                                                           Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        projectAccess.requireAccess(todo.projectId(), user);
        return evidence.forTodo(todoId);
    }

    public record ConfirmTodo(Long assigneeId, LocalDate dueDate) {}

    @PostMapping("/api/todos/{todoId}/confirm")
    public Map<String, Object> confirm(@PathVariable long todoId,
                                       @RequestBody ConfirmTodo request,
                                       Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem before = todos.find(todoId);
        projectAccess.requireAdmin(before.projectId(), user);
        todoService.confirm(before, request.assigneeId(), request.dueDate(), user);
        return Map.of("status", "CONFIRMED");
    }

    @PostMapping("/api/todos/{todoId}/reject")
    public Map<String,Object> reject(@PathVariable long todoId,Authentication authentication){
        User user=currentUser.requireOperational(authentication);
        TodoItem before=todos.find(todoId);
        projectAccess.requireAdmin(before.projectId(),user);
        todoService.reject(before,user);
        return Map.of("status","REJECTED");
    }


    @PostMapping("/api/todos/{todoId}/merge-duplicate")
    public Map<String,Object> mergeDuplicate(@PathVariable long todoId,Authentication authentication){
        User user=currentUser.requireOperational(authentication);
        TodoItem candidate=todos.find(todoId);
        projectAccess.requireAdmin(candidate.projectId(),user);
        todoService.mergeDuplicate(candidate,user);
        return Map.of("status","MERGED");
    }

    public record StatusChange(String status) {}

    @PatchMapping("/api/todos/{todoId}/status")
    public Map<String, Object> status(@PathVariable long todoId,
                                      @RequestBody StatusChange request,
                                      Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        long projectId = todo.projectId();
        projectAccess.requireAccess(projectId, user);

        if (!projectAccess.isAdmin(projectId, user)) {
            if (!"CONFIRMED".equals(todo.reviewStatus())) {
                throw new AccessDeniedException("Only confirmed TODOs can be updated by assignees");
            }
            if (todo.assigneeId() == null || todo.assigneeId() != user.id()) {
                throw new AccessDeniedException("Only the assignee or ADMIN can update this TODO");
            }
        }

        String status = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!TASK_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid task status");
        }
        todoService.updateStatus(todo, status, user);
        return Map.of("status", status);
    }
}
