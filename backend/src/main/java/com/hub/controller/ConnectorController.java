// connector credentials are personal to each account; any project member may link their own and import
// with it - what they import always lands in the current project's shared document pool.
package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.service.ConnectorService;
import com.hub.service.CurrentUserService;
import com.hub.service.ProcessingJobService;
import com.hub.service.ProjectAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/connectors")
public class ConnectorController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final ConnectorService connectorService;
    private final ProcessingJobService jobs;
    private final AuditRepository audit;

    public ConnectorController(CurrentUserService currentUser, ProjectAccessService projectAccess,
                               ConnectorService connectorService, ProcessingJobService jobs, AuditRepository audit) {
        this.currentUser = currentUser; this.projectAccess = projectAccess; this.connectorService = connectorService;
        this.jobs = jobs; this.audit = audit;
    }

    public record ImportRequest(String scope) {}

    @GetMapping("/status")
    public Object status(@PathVariable long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication); projectAccess.requireAccess(projectId, user);
        return connectorService.syncStates(projectId);
    }

    /** What this account may import from. The screen offers these instead of a free-text scope box. */
    @GetMapping("/{type}/targets")
    public Map<String,Object> targets(@PathVariable long projectId, @PathVariable String type,
                                      Authentication authentication) {
        User user = currentUser.requireOperational(authentication); projectAccess.requireAccess(projectId, user);
        return connectorService.targets(type, user);
    }

    /** Removes this account's own link; the shared server credential (if any) takes over again. */
    @DeleteMapping("/{type}/link")
    public Map<String,Object> disconnect(@PathVariable long projectId, @PathVariable String type,
                                         Authentication authentication) {
        User user = currentUser.requireOperational(authentication); projectAccess.requireAccess(projectId, user);
        connectorService.disconnect(type, user);
        audit.add(user.id(), projectId, "CONNECTOR_UNLINK", type.toUpperCase(), null, "{}");
        return Map.of("status", "UNLINKED");
    }

    /**
     * Runs in the background (ProcessingJobExecutor.connectorImport) instead of blocking this
     * request - a folder/repo/channel with a lot of content could otherwise tie up the request
     * thread and time out the browser long before the import itself finished. Audit logging moves
     * with it, into the executor, since the imported count isn't known until the job completes.
     */
    @PostMapping("/{type}/import")
    public ResponseEntity<Map<String,Object>> importItems(@PathVariable long projectId, @PathVariable String type,
                                          @RequestBody ImportRequest request, Authentication authentication) {
        User user = currentUser.requireOperational(authentication); projectAccess.requireAccess(projectId, user);
        long jobId = jobs.queueConnectorImport(projectId, type, request.scope(), user);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "PENDING"));
    }
}
