package com.hub.service;

import com.hub.repository.ConnectorRepository;
import com.hub.repository.DocumentRepository;
import com.hub.repository.MeetingRepository;
import com.hub.repository.ProjectRepository;
import com.hub.repository.RefreshTokenRepository;
import com.hub.repository.SearchLogRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DataRetentionServiceProjectPolicyTest {
    @Test
    void usesEachProjectsConfiguredRetentionWindow() {
        RefreshTokenRepository refresh = mock(RefreshTokenRepository.class);
        SearchLogRepository search = mock(SearchLogRepository.class);
        ConnectorRepository connectors = mock(ConnectorRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        MeetingRepository meetings = mock(MeetingRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);

        when(projects.listRetentionPolicies()).thenReturn(List.of(
                new ProjectRepository.RetentionPolicy(10L, 3),
                new ProjectRepository.RetentionPolicy(20L, 12)
        ));

        DataRetentionService service = new DataRetentionService(
                refresh, search, connectors, documents, meetings, projects, true
        );
        service.purgeOldData();

        LocalDate today = LocalDate.now();
        verify(refresh).deleteExpired();
        verify(search).purgeOlderThan(10L, today.minusMonths(3));
        verify(search).purgeOlderThan(20L, today.minusMonths(12));
        verify(connectors).purgeOrphanedItemsOlderThan(10L, today.minusMonths(3));
        verify(connectors).purgeOrphanedItemsOlderThan(20L, today.minusMonths(12));
        verify(documents).purgeArchivedContentOlderThan(10L, today.minusMonths(3));
        verify(documents).purgeArchivedContentOlderThan(20L, today.minusMonths(12));
        verify(meetings).purgeTranscriptsOlderThan(10L, today.minusMonths(3));
        verify(meetings).purgeTranscriptsOlderThan(20L, today.minusMonths(12));
    }
}
