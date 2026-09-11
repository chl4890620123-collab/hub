package com.hub.service;

import com.hub.model.SearchHit;
import com.hub.repository.DocumentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("!postgresql")
public class LocalLexicalSearchService implements LexicalSearchService {
    private final DocumentRepository documents;

    public LocalLexicalSearchService(DocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public List<SearchHit> searchNative(long projectId, String query, int limit) {
        return documents.searchNative(projectId, query, limit);
    }
}
