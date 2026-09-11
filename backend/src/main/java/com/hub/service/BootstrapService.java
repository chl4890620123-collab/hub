// The first ADMIN self-registers; later ADMIN accounts require approval by an existing ADMIN.
package com.hub.service;

import com.hub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);
    private final UserRepository users;

    public BootstrapService(UserRepository users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.countApprovedAdmins() == 0) {
            log.warn("No active ADMIN exists. Open /login > 관리자 가입 to create the first ADMIN.");
        }
    }
}
