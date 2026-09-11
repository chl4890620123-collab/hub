package com.hub.controller;

import com.hub.model.MaterialHit;
import com.hub.model.User;
import com.hub.repository.AuditRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.EmbeddingMaintenanceService;
import com.hub.service.MaterialSearchService;
import com.hub.service.ProjectAccessService;
import com.hub.service.SearchRuleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/projects/{projectId}/search")
public class SearchRuleAdminController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService access;
    private final SearchRuleService rules;
    private final MaterialSearchService materials;
    private final EmbeddingMaintenanceService embeddings;
    private final AuditRepository audit;

    public SearchRuleAdminController(CurrentUserService currentUser,
                                     ProjectAccessService access,
                                     SearchRuleService rules,
                                     MaterialSearchService materials,
                                     EmbeddingMaintenanceService embeddings,
                                     AuditRepository audit) {
        this.currentUser = currentUser;
        this.access = access;
        this.rules = rules;
        this.materials = materials;
        this.embeddings = embeddings;
        this.audit = audit;
    }

    @GetMapping("/rules")
    public List<SearchRuleService.SearchRule> list(@PathVariable long projectId, Authentication authentication) {
        admin(projectId, authentication);
        return rules.managedRules(projectId);
    }

    @PostMapping("/rules")
    public SearchRuleService.SearchRule create(@PathVariable long projectId,
                                               @RequestBody SearchRuleService.RuleInput input,
                                               Authentication authentication) {
        User actor = admin(projectId, authentication);
        SearchRuleService.SearchRule saved = rules.create(projectId, input, actor.id());
        audit.add(actor.id(), projectId, "SEARCH_RULE_CREATE", "SEARCH_RULE", saved.id(), "{}");
        return saved;
    }

    @PutMapping("/rules/{ruleId}")
    public SearchRuleService.SearchRule update(@PathVariable long projectId,
                                               @PathVariable long ruleId,
                                               @RequestBody SearchRuleService.RuleInput input,
                                               Authentication authentication) {
        User actor = admin(projectId, authentication);
        SearchRuleService.SearchRule saved = rules.update(projectId, ruleId, input);
        audit.add(actor.id(), projectId, "SEARCH_RULE_UPDATE", "SEARCH_RULE", ruleId, "{}");
        return saved;
    }

    @DeleteMapping("/rules/{ruleId}")
    public Map<String,Object> delete(@PathVariable long projectId,
                                     @PathVariable long ruleId,
                                     Authentication authentication) {
        User actor = admin(projectId, authentication);
        rules.delete(projectId, ruleId);
        audit.add(actor.id(), projectId, "SEARCH_RULE_DELETE", "SEARCH_RULE", ruleId, "{}");
        return Map.of("status", "DELETED");
    }

    @GetMapping("/test")
    public Map<String,Object> test(@PathVariable long projectId,
                                   @RequestParam String q,
                                   Authentication authentication) {
        admin(projectId, authentication);
        var matched = rules.match(projectId, q).orElse(null);
        List<MaterialHit> results = materials.search(projectId, q).stream().limit(5).toList();
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("query", q);
        response.put("matchedRule", matched);
        response.put("results", results);
        return response;
    }

    @GetMapping("/embedding-status")
    public Map<String,Object> embeddingStatus(@PathVariable long projectId, Authentication authentication) {
        admin(projectId, authentication);
        return embeddings.health(projectId);
    }

    @PostMapping("/embedding-retry")
    public Map<String,Object> embeddingRetry(@PathVariable long projectId, Authentication authentication) {
        admin(projectId, authentication);
        int ready = embeddings.retryProject(projectId, 20);
        return Map.of("reindexed", ready, "status", "COMPLETED");
    }

    private User admin(long projectId, Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        access.requireAdmin(projectId, user);
        return user;
    }
}
