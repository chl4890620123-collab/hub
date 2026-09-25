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
import org.springframework.web.bind.annotation.DeleteMapping;
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
    // DONE and BLOCKED are reachable only through the dedicated completion/help endpoints below, which
    // capture the approval and the help-request note that a bare status PATCH can't carry.
    private static final List<String> TASK_STATUSES = List.of("TODO", "IN_PROGRESS");

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

    @GetMapping("/api/projects/{projectId}/todos/due-through")
    public List<TodoItem> dueThrough(@PathVariable long projectId,
                                     @RequestParam LocalDate date,
                                     Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return todoService.dueThrough(projectId, date);
    }

    @GetMapping("/api/projects/{projectId}/todos/trash")
    public List<TodoItem> trash(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        return todoService.trash(projectId);
    }

    @GetMapping("/api/projects/{projectId}/review/todos")
    public List<TodoItem> pending(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireConfirmPermission(projectId, user);
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
        projectAccess.requireConfirmPermission(before.projectId(), user);
        todoService.confirm(before, request.assigneeId(), request.dueDate(), user);
        return Map.of("status", "CONFIRMED");
    }

    public record EditCandidate(String title, String description) {}

    @PatchMapping("/api/todos/{todoId}")
    public Map<String,Object> editCandidate(@PathVariable long todoId,
                                            @RequestBody EditCandidate request,
                                            Authentication authentication){
        User user=currentUser.requireOperational(authentication);
        TodoItem before=todos.find(todoId);
        projectAccess.requireConfirmPermission(before.projectId(),user);
        todoService.editCandidate(before,request.title(),request.description(),user);
        return Map.of("status","UPDATED");
    }

    public record BulkConfirm(List<Long> todoIds, Long assigneeId, LocalDate dueDate) {}

    @PostMapping("/api/projects/{projectId}/review/todos/bulk-confirm")
    public Map<String,Object> bulkConfirm(@PathVariable long projectId,
                                          @RequestBody BulkConfirm request,
                                          Authentication authentication){
        User user=currentUser.requireOperational(authentication);
        projectAccess.requireConfirmPermission(projectId,user);
        if(request.todoIds()==null||request.todoIds().isEmpty())throw new IllegalArgumentException("선택된 할 일이 없습니다.");
        Map<Long,String> results=todoService.bulkConfirm(projectId,request.todoIds(),request.assigneeId(),request.dueDate(),user);
        return Map.of("results",results);
    }

    @PostMapping("/api/todos/{todoId}/reject")
    public Map<String,Object> reject(@PathVariable long todoId,Authentication authentication){
        User user=currentUser.requireOperational(authentication);
        TodoItem before=todos.find(todoId);
        projectAccess.requireConfirmPermission(before.projectId(),user);
        todoService.reject(before,user);
        return Map.of("status","REJECTED");
    }


    @PostMapping("/api/todos/{todoId}/merge-duplicate")
    public Map<String,Object> mergeDuplicate(@PathVariable long todoId,Authentication authentication){
        User user=currentUser.requireOperational(authentication);
        TodoItem candidate=todos.find(todoId);
        projectAccess.requireConfirmPermission(candidate.projectId(),user);
        todoService.mergeDuplicate(candidate,user);
        return Map.of("status","MERGED");
    }

    @PostMapping("/api/todos/{todoId}/delete")
    public Map<String,Object> softDelete(@PathVariable long todoId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        requireDeletePermission(todo, user);
        todoService.softDelete(todo, user);
        return Map.of("status", "DELETED");
    }

    @PostMapping("/api/todos/{todoId}/restore")
    public Map<String,Object> restore(@PathVariable long todoId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        requireDeletePermission(todo, user);
        todoService.restore(todo, user);
        return Map.of("status", "RESTORED");
    }

    @DeleteMapping("/api/todos/{todoId}/permanent")
    public Map<String,Object> permanentDelete(@PathVariable long todoId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        projectAccess.requireConfirmPermission(todo.projectId(), user);
        todoService.permanentDelete(todo, user);
        return Map.of("status", "PERMANENTLY_DELETED");
    }

    public record StatusChange(String status) {}

    @PatchMapping("/api/todos/{todoId}/status")
    public Map<String, Object> status(@PathVariable long todoId,
                                      @RequestBody StatusChange request,
                                      Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        requireAssigneeOrAdmin(todo, user);

        String status = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!TASK_STATUSES.contains(status)) {
            throw new IllegalArgumentException("변경할 수 없는 할 일 상태입니다.");
        }
        todoService.updateStatus(todo, status, user);
        return Map.of("status", status);
    }

    /** Decision-makers can remove mistakes; an assignee may clean up only their own completed work. */
    private void requireDeletePermission(TodoItem todo, User user) {
        projectAccess.requireAccess(todo.projectId(), user);
        if (projectAccess.isAdmin(todo.projectId(), user)) return;
        if ("CONFIRMED".equals(todo.reviewStatus()) && "DONE".equals(todo.taskStatus())
                && todo.assigneeId() != null && todo.assigneeId() == user.id()) return;
        projectAccess.requireConfirmPermission(todo.projectId(), user);
    }

    /** The assignee, or an ADMIN acting on their behalf - same rule the generic status PATCH already used. */
    private void requireAssigneeOrAdmin(TodoItem todo, User user) {
        projectAccess.requireAccess(todo.projectId(), user);
        if (projectAccess.isAdmin(todo.projectId(), user)) return;
        if (!"CONFIRMED".equals(todo.reviewStatus()) || todo.assigneeId() == null || todo.assigneeId() != user.id()) {
            throw new AccessDeniedException("담당자 또는 관리자만 이 할 일의 상태를 변경할 수 있습니다.");
        }
    }

    @PostMapping("/api/todos/{todoId}/request-completion")
    public Map<String, Object> requestCompletion(@PathVariable long todoId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        requireAssigneeOrAdmin(todo, user);
        todoService.requestCompletion(todo, user);
        return Map.of("status", "PENDING_APPROVAL");
    }

    @PostMapping("/api/todos/{todoId}/approve-completion")
    public Map<String, Object> approveCompletion(@PathVariable long todoId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        projectAccess.requireConfirmPermission(todo.projectId(), user);
        todoService.approveCompletion(todo, user);
        return Map.of("status", "DONE");
    }

    public record RejectCompletion(String reason) {}

    @PostMapping("/api/todos/{todoId}/reject-completion")
    public Map<String, Object> rejectCompletion(@PathVariable long todoId, @RequestBody(required = false) RejectCompletion request,
                                                Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        projectAccess.requireConfirmPermission(todo.projectId(), user);
        todoService.rejectCompletion(todo, request == null ? null : request.reason(), user);
        return Map.of("status", "IN_PROGRESS");
    }

    public record HelpRequest(String note) {}

    @PostMapping("/api/todos/{todoId}/request-help")
    public Map<String, Object> requestHelp(@PathVariable long todoId, @RequestBody HelpRequest request,
                                           Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        requireAssigneeOrAdmin(todo, user);
        todoService.requestHelp(todo, request.note(), user);
        return Map.of("status", "BLOCKED");
    }

    @PostMapping("/api/todos/{todoId}/resolve-help")
    public Map<String, Object> resolveHelp(@PathVariable long todoId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        TodoItem todo = todos.find(todoId);
        requireAssigneeOrAdmin(todo, user);
        todoService.resolveHelp(todo, user);
        return Map.of("status", "IN_PROGRESS");
    }
}
