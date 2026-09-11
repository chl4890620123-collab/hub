package com.hub.service;

import com.hub.model.ProjectMemory;
import com.hub.model.User;
import com.hub.repository.ProjectMemoryRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProjectMemoryService {
    private final ProjectMemoryRepository repository;
    public ProjectMemoryService(ProjectMemoryRepository repository){this.repository=repository;}
    public List<ProjectMemory> list(long projectId){return repository.list(projectId);}
    public void put(long projectId,String key,String value,String type,String sourceType,Long sourceId,User actor){
        repository.upsert(projectId,key,value,type,sourceType,sourceId,actor.id());
    }
}
