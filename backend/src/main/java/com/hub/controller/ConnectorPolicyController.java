// Company-wide switch for which connector types anyone may link/import from, separate from the
// per-project/per-account access already enforced in ConnectorController - see ConnectorService.
package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.repository.ConnectorPolicyRepository;
import com.hub.service.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/connector-policy")
public class ConnectorPolicyController {
    private final CurrentUserService currentUser;
    private final ConnectorPolicyRepository policy;
    private final AuditRepository audit;

    public ConnectorPolicyController(CurrentUserService currentUser, ConnectorPolicyRepository policy, AuditRepository audit) {
        this.currentUser = currentUser;
        this.policy = policy;
        this.audit = audit;
    }

    /** Any operational user reads this - the connector cards need it to know what to show. */
    @GetMapping
    public Map<String, Boolean> list(Authentication authentication) {
        currentUser.requireOperational(authentication);
        return policy.list();
    }

    public record PolicyChange(boolean enabled) {}

    @PutMapping("/{type}")
    public Map<String, Object> update(@PathVariable String type, @RequestBody PolicyChange request, Authentication authentication) {
        User admin = currentUser.requireOperational(authentication);
        if (!admin.isAdmin()) throw new AccessDeniedException("관리자 권한이 필요합니다.");
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        policy.setEnabled(normalized, request.enabled());
        audit.add(admin.id(), null, "CONNECTOR_POLICY_CHANGE", "CONNECTOR", null,
                "{\"type\":\"" + normalized + "\",\"enabled\":" + request.enabled() + "}");
        return Map.of("status", "UPDATED");
    }
}
