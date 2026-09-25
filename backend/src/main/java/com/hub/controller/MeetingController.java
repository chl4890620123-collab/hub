// Browser recording or uploaded audio returns an idempotent background STT/analysis job for polling.
package com.hub.controller;

import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.MeetingService;
import com.hub.service.ProcessingJobService;
import com.hub.service.ProjectAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/meetings")
public class MeetingController {
    private final CurrentUserService currentUser;
    private final ProjectAccessService projectAccess;
    private final MeetingService meetingService;
    private final ProcessingJobService jobs;

    public MeetingController(CurrentUserService currentUser, ProjectAccessService projectAccess,
                             MeetingService meetingService, ProcessingJobService jobs) {
        this.currentUser = currentUser; this.projectAccess = projectAccess; this.meetingService = meetingService; this.jobs = jobs;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(@PathVariable long projectId,
                                      @RequestParam String title,
                                      @RequestParam(required = false) OffsetDateTime meetingAt,
                                      @RequestPart("file") MultipartFile file,
                                      Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        var uploaded = meetingService.createUpload(projectId, title, meetingAt, file, user);
        long jobId = jobs.queueMeeting(projectId, uploaded.meetingId());
        return ResponseEntity.accepted().body(Map.<String,Object>of("meetingId", uploaded.meetingId(), "jobId", jobId, "status", "PENDING"));
    }

    @DeleteMapping("/{meetingId}")
    public Map<String, Object> delete(@PathVariable long projectId, @PathVariable long meetingId,
                                      Authentication authentication) {
        User user = currentUser.requireOperational(authentication);
        projectAccess.requireAccess(projectId, user);
        meetingService.deleteCompleted(projectId, meetingId, user);
        return Map.of("status", "DELETED", "meetingId", meetingId);
    }
}
