package com.hub.controller;

/**
 * Builds the callback address a provider must redirect back to.
 *
 * Deriving it from the incoming request breaks the moment the browser reaches Hub by a different
 * name than the one registered with the provider — localhost and 127.0.0.1 are the usual pair, and
 * a LAN address is another. HUB_PUBLIC_BASE_URL pins it so every provider sees one stable value.
 */
final class OAuthRedirects {
    private OAuthRedirects() {}

    static String callbackUri(String configuredBaseUrl, String requestUrl, String authorizePath, String callbackPath) {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            String base = configuredBaseUrl.trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + callbackPath;
        }
        return requestUrl.replace(authorizePath, callbackPath);
    }
}
