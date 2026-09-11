package com.hub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** JWT and login hardening settings. Values are externalized so secrets are never hard-coded in source. */
@ConfigurationProperties(prefix = "hub.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        String audience,
        Duration accessTtl,
        Duration refreshTtl,
        boolean secureCookies,
        String sameSite,
        int maxFailedAttempts,
        Duration lockDuration,
        boolean enforceSecureConfig
) {
    public static final String ACCESS_COOKIE = "HUB_ACCESS_TOKEN";
    public static final String REFRESH_COOKIE = "HUB_REFRESH_TOKEN";
}
