package com.hub.controller;

import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.service.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {
    private final CurrentUserService current;
    private final AuditRepository audit;
    public AuditController(CurrentUserService current, AuditRepository audit) { this.current=current; this.audit=audit; }

    @GetMapping
    public List<Map<String,Object>> list(Authentication auth) {
        User user=current.requireOperational(auth);
        if(!user.isAdmin()) throw new AccessDeniedException("관리자 권한이 필요합니다.");
        return audit.list(300);
    }
}
