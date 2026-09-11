package com.hub.controller;

import com.hub.model.TimelineEvent;
import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.ProjectAccessService;
import com.hub.service.TimelineService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/timeline")
public class TimelineController {
    private final CurrentUserService current;private final ProjectAccessService access;private final TimelineService timeline;
    public TimelineController(CurrentUserService current,ProjectAccessService access,TimelineService timeline){this.current=current;this.access=access;this.timeline=timeline;}
    @GetMapping public List<TimelineEvent> list(@PathVariable long projectId,Authentication auth){User u=current.requireOperational(auth);access.requireAccess(projectId,u);return timeline.list(projectId);}
}
