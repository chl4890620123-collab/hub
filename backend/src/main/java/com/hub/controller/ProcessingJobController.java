// web poll this endpoint for long file/STT processing instead of keeping one HTTP request open.
package com.hub.controller;

import com.hub.model.ProcessingJob;
import com.hub.model.User;
import com.hub.repository.ProcessingJobRepository;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        if (job.requesterUserId() != null && job.requesterUserId() != user.id())
            throw new org.springframework.security.access.AccessDeniedException("이 AI 작업은 요청한 사용자만 볼 수 있습니다.");
        return job;
    }

    @GetMapping("/projects/{projectId}/jobs")
    public List<ProcessingJob> recent(@PathVariable long projectId, Authentication authentication) {
        User user = current.requireOperational(authentication);
        access.requireAccess(projectId, user);
        return jobs.recentVisible(projectId, user.id());
    }
}
