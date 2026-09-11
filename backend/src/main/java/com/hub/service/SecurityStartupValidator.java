// production startup validator, including first-ADMIN setup-key safety.
package com.hub.service;

import com.hub.config.JwtProperties;
import com.hub.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityStartupValidator implements ApplicationRunner {
    private final JwtProperties jwt;
    private final UserRepository users;

    public SecurityStartupValidator(JwtProperties jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateDurations();
        if (!jwt.enforceSecureConfig()) return;

        String secret = jwt.secret() == null ? "" : jwt.secret();
        String lowered = secret.toLowerCase(Locale.ROOT);
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32
                || lowered.contains("local-only")
                || lowered.contains("changeme")
                || lowered.contains("replace_me")) {
            throw new IllegalStateException("Production startup refused: set a strong HUB_JWT_SECRET (32+ random bytes).");
        }
        if (!jwt.secureCookies()) {
            throw new IllegalStateException("Production startup refused: HUB_COOKIE_SECURE must be true behind HTTPS.");
        }
    }

    private void validateDurations() {
        Duration access = jwt.accessTtl();
        Duration refresh = jwt.refreshTtl();
        if (access == null || access.isZero() || access.isNegative()) throw new IllegalStateException("HUB_JWT_ACCESS_TTL must be positive.");
        if (refresh == null || refresh.isZero() || refresh.isNegative() || refresh.compareTo(access) <= 0)
            throw new IllegalStateException("HUB_JWT_REFRESH_TTL must be longer than the access token TTL.");
        if (jwt.enforceSecureConfig() && access.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalStateException("Production access token TTL must not exceed 1 hour.");
    }
}
