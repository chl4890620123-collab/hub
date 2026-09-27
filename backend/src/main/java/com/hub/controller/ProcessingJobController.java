// web poll this endpoint for long file/STT processing instead of keeping one HTTP request open.
package com.hub.controller;

import com.hub.model.ProcessingJob;
import com.hub.model.User;
import com.hub.repository.ProcessingJobRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProcessingJobController {
    private final ProcessingJobRepository jobs;
    private final CurrentUserService current;
    private final ProjectAccessService access;

    public ProcessingJobController(ProcessingJobRepository jobs, CurrentUserService current, ProjectAccessService access) {
        this.jobs = jobs; this.current = current; this.access = access;
    }

    @GetMapping("/jobs/{jobId}")
    public ProcessingJob job(@PathVariable long jobId, Authentication authentication) {
        User user = current.requireOperational(authentication);
        ProcessingJob job = jobs.find(jobId);
        access.requireAccess(job.projectId(), user);
        return job;
    }

    @DeleteMapping("/jobs/{jobId}")
    public Map<String, Object> deleteFailed(@PathVariable long jobId, Authentication authentication) {
        User user = current.requireOperational(authentication);
        ProcessingJob job = jobs.find(jobId);
        access.requireAccess(job.projectId(), user);
        if (!"FAILED".equals(job.status())) {
            throw new IllegalArgumentException("실패한 분석 기록만 삭제할 수 있습니다.");
        }
        if (!jobs.deleteFailed(jobId)) {
            throw new IllegalStateException("실패한 분석 기록 상태가 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
        return Map.of("status", "DELETED");
    }

    @GetMapping("/projects/{projectId}/jobs")
    public List<ProcessingJob> recent(@PathVariable long projectId, Authentication authentication) {
        User user = current.requireOperational(authentication);
        access.requireAccess(projectId, user);
        return jobs.recent(projectId);
    }
}
