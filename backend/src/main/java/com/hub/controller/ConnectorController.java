// connector credentials are personal to each account; any project member may link their own and import
// with it - what they import always lands in the current project's shared document pool.
package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.service.ConnectorService;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/connectors")
public class ConnectorController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final ConnectorService connectorService;
    private final AuditRepository audit;

    public ConnectorController(CurrentUserService currentUser, ProjectAccessService projectAccess,
                               ConnectorService connectorService, AuditRepository audit) {
        this.currentUser = currentUser; this.projectAccess = projectAccess; this.connectorService = connectorService; this.audit = audit;
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

    @PostMapping("/{type}/import")
    public Map<String,Object> importItems(@PathVariable long projectId, @PathVariable String type,
                                          @RequestBody ImportRequest request, Authentication authentication) {
        User user = currentUser.requireOperational(authentication); projectAccess.requireAccess(projectId, user);
        int count = connectorService.importItems(projectId, type, request.scope(), user);
        audit.add(user.id(), projectId, "CONNECTOR_IMPORT", type.toUpperCase(), null, "{\"count\":" + count + "}");
        return Map.of("imported", count);
    }
}
