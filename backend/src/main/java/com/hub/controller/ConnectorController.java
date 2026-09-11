// connector credentials come only from server environment; UI submits scope and ADMIN triggers read-only snapshot sync.
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

    @PostMapping("/{type}/import")
    public Map<String,Object> importItems(@PathVariable long projectId, @PathVariable String type,
                                          @RequestBody ImportRequest request, Authentication authentication) {
        User user = currentUser.requireOperational(authentication); projectAccess.requireAdmin(projectId, user);
        int count = connectorService.importItems(projectId, type, request.scope(), user);
        audit.add(user.id(), projectId, "CONNECTOR_IMPORT", type.toUpperCase(), null, "{\"count\":" + count + "}");
        return Map.of("imported", count);
    }
}
