package com.hub.controller;

import com.hub.model.ProcessingJob;
import com.hub.model.ProjectNotification;
import com.hub.model.User;
import com.hub.repository.ConnectorRepository;
import com.hub.repository.ProcessingJobRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.GitHubNotificationService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
public class ProjectNotificationController {
    private final CurrentUserService current;
    private final ProjectAccessService access;
    private final ConnectorRepository connectors;
    private final ProcessingJobRepository jobs;
    private final GitHubNotificationService github;

    public ProjectNotificationController(CurrentUserService current, ProjectAccessService access,
                                         ConnectorRepository connectors, ProcessingJobRepository jobs,
                                         GitHubNotificationService github) {
        this.current = current;
        this.access = access;
        this.connectors = connectors;
        this.jobs = jobs;
        this.github = github;
    }

    @GetMapping("/api/projects/{projectId}/notifications")
    public List<ProjectNotification> notifications(@PathVariable long projectId, Authentication authentication) {
        User user = current.requireOperational(authentication);
        access.requireAccess(projectId, user);

        List<ProjectNotification> out = new ArrayList<>();
        for (ConnectorRepository.SyncState state : connectors.listSyncStates(projectId)) {
            if (!"FAILED".equalsIgnoreCase(state.lastStatus())) continue;
            out.add(new ProjectNotification(
                    "connector:" + state.connectorType() + ":" + state.externalScope(),
                    "CONNECTOR",
                    label(state.connectorType()),
                    "ERROR",
                    label(state.connectorType()) + " 자료 가져오기 실패",
                    state.lastError() == null || state.lastError().isBlank() ? "연결 상태를 확인해 주세요." : state.lastError(),
                    state.lastSyncedAt() == null ? "" : state.lastSyncedAt().toString(),
                    null
            ));
        }

        for (ProcessingJob job : jobs.recent(projectId)) {
            if (!"FAILED".equalsIgnoreCase(job.status()) || "CONNECTOR_IMPORT".equalsIgnoreCase(job.jobType())) continue;
            out.add(new ProjectNotification(
                    "job:" + job.id(),
                    "PROCESSING",
                    job.jobType(),
                    "ERROR",
                    jobLabel(job.jobType()) + " 실패",
                    job.errorMessage() == null || job.errorMessage().isBlank() ? "처리 기록을 확인해 주세요." : job.errorMessage(),
                    job.updatedAt() == null ? "" : job.updatedAt().toString(),
                    null
            ));
        }

        out.addAll(github.workflowAlerts(projectId, user.id()));
        out.sort(Comparator.comparing(ProjectNotification::occurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return out.stream().limit(100).toList();
    }

    private static String label(String type) {
        if (type == null) return "연결 서비스";
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "GITHUB" -> "GitHub";
            case "GOOGLE_DRIVE" -> "Google Drive";
            case "SLACK" -> "Slack";
            case "NOTION" -> "Notion";
            default -> type;
        };
    }

    private static String jobLabel(String type) {
        if (type == null) return "백그라운드 작업";
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "DOCUMENT_ANALYSIS" -> "문서 AI 분석";
            case "MEETING_ANALYSIS" -> "회의 AI 분석";
            case "MEETING_TRANSCRIPTION" -> "회의 음성 변환";
            default -> "백그라운드 작업";
        };
    }
}
