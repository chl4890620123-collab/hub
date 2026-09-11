package com.hub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime configuration for storage, AI providers, connectors, read-only local ingestion, and document-first search. */
@ConfigurationProperties(prefix = "hub")
public record HubProperties(
        String storageRoot,
        String aiBaseUrl,
        boolean demoMode,
        String localReaderRoots,
        String searchRulesFile,
        int searchWholeDocumentMaxChars,
        int searchNeighborChunks,
        int searchMaxDocuments,
        int ragMaxChunks,
        int ragMaxContextChars,
        String githubToken,
        String googleAccessToken,
        String googleClientId,
        String googleClientSecret,
        String googleRefreshToken,
        String slackToken,
        String notionToken,
        String githubClientId,
        String githubClientSecret,
        String slackClientId,
        String slackClientSecret,
        String notionClientId,
        String notionClientSecret
) {
    /** Keeps existing unit-test and local construction code source-compatible. */
    public HubProperties(String storageRoot, String aiBaseUrl, boolean demoMode, String localReaderRoots,
                         String searchRulesFile, int searchWholeDocumentMaxChars, int searchNeighborChunks,
                         int searchMaxDocuments, int ragMaxChunks, int ragMaxContextChars,
                         String githubToken, String googleAccessToken, String googleClientId,
                         String googleClientSecret, String googleRefreshToken, String slackToken,
                         String notionToken) {
        this(storageRoot, aiBaseUrl, demoMode, localReaderRoots, searchRulesFile,
                searchWholeDocumentMaxChars, searchNeighborChunks, searchMaxDocuments, ragMaxChunks,
                ragMaxContextChars, githubToken, googleAccessToken, googleClientId, googleClientSecret,
                googleRefreshToken, slackToken, notionToken, null, null, null, null, null, null);
    }
}
