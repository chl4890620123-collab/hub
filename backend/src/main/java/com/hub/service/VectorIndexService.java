package com.hub.service;

import com.hub.model.SearchHit;
import java.util.List;

public interface VectorIndexService {
    void store(long chunkId, List<Float> vector);
    List<SearchHit> nearest(long projectId, List<Float> queryVector, int limit);
}
