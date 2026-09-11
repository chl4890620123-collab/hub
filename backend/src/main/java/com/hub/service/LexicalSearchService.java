package com.hub.service;

import com.hub.model.SearchHit;

import java.util.List;

/** Database-profile-specific lexical retrieval used by the hybrid recommendation search. */
public interface LexicalSearchService {
    List<SearchHit> searchNative(long projectId, String query, int limit);
}
