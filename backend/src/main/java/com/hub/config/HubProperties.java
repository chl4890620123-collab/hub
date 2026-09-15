package com.hub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Runtime configuration for storage, AI providers, connectors, read-only local ingestion, and document-first search.
 * {@code allowSharedConnectorFallback} defaults to false: without it, a connector token configured here
 * (e.g. an operator's own GOOGLE_REFRESH_TOKEN left over from local setup) is used only for the account
 * that linked it, never as an implicit fallback for every other user of the connector.
 */
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
        String slackTeamId,
        String notionClientId,
        String notionClientSecret,
        String publicBaseUrl,
        boolean allowSharedConnectorFallback
) {
    /**
     * The convenience constructor below makes this record have more than one constructor, so Spring cannot
     * pick a binding constructor on its own and falls back to JavaBean binding, which a record cannot satisfy.
     * Marking the canonical constructor keeps configuration binding on the value-object path.
     */
    @ConstructorBinding
    public HubProperties {
    }

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
                googleRefreshToken, slackToken, notionToken, null, null, null, null, null, null, null, null, false);
    }
}
