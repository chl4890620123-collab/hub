// Admin signup never self-approves (see SignupService.registerAdmin), so a fresh install with zero
// admins has no way to get one through the app itself - approving a signup requires an existing admin.
// This creates exactly one, but only from a password the OPERATOR chooses via HUB_BOOTSTRAP_ADMIN_PASSWORD
// (never a value this codebase generates or hardcodes) - that variable is expected to live only in the
// operator's own untracked .env/secret store, never in git. It is a no-op once any admin already exists.
package com.hub.service;

import com.hub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final String bootstrapLoginId;
    private final String bootstrapPassword;

    public BootstrapService(UserRepository users, PasswordEncoder encoder, PasswordPolicy passwordPolicy,
                            @Value("${hub.bootstrap-admin-login-id:admin}") String bootstrapLoginId,
                            @Value("${hub.bootstrap-admin-password:}") String bootstrapPassword) {
        this.users = users;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.bootstrapLoginId = bootstrapLoginId == null || bootstrapLoginId.isBlank() ? "admin" : bootstrapLoginId.trim();
        this.bootstrapPassword = bootstrapPassword == null ? "" : bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.countApprovedAdmins() > 0) return;
        if (bootstrapPassword.isBlank()) {
            log.warn("No active ADMIN exists yet and HUB_BOOTSTRAP_ADMIN_PASSWORD is not set, so no one can "
                    + "log in to approve anyone. Set HUB_BOOTSTRAP_ADMIN_PASSWORD (and optionally "
                    + "HUB_BOOTSTRAP_ADMIN_LOGIN_ID, default 'admin') in this server's own .env and restart.");
            return;
        }
        try {
            passwordPolicy.validate(bootstrapPassword);
        } catch (IllegalArgumentException invalid) {
            log.warn("HUB_BOOTSTRAP_ADMIN_PASSWORD does not meet the password policy ({}); no bootstrap admin was created.",
                    invalid.getMessage());
            return;
        }
        users.createBootstrapAdmin(bootstrapLoginId, bootstrapLoginId + "@bootstrap.local",
                encoder.encode(bootstrapPassword), "관리자");
        log.warn("Created bootstrap ADMIN '{}' from HUB_BOOTSTRAP_ADMIN_PASSWORD. It must change its password on first login.",
                bootstrapLoginId);
    }
}
