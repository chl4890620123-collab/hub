package com.hub.service;

import com.hub.model.TimelineEvent;
import com.hub.repository.TimelineRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TimelineService {
    private final TimelineRepository repository;
    public TimelineService(TimelineRepository repository){this.repository=repository;}
    public List<TimelineEvent> list(long projectId){return repository.list(projectId,200);}
}
