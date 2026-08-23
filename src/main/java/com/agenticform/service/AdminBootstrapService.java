package com.agenticform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;
import com.agenticform.repository.UserRepository;

/**
 * Promouvoir un compte existant en {@link Role#ROLE_ADMIN} (inbox Contact Mongo).
 * Config : {@code ADMIN_BOOTSTRAP_EMAIL} / {@code app.admin.bootstrap-email}.
 */
@Service
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final String bootstrapEmail;

    public AdminBootstrapService(
            UserRepository userRepository,
            @Value("${app.admin.bootstrap-email:}") String bootstrapEmail) {
        this.userRepository = userRepository;
        this.bootstrapEmail = bootstrapEmail == null ? "" : bootstrapEmail.trim().toLowerCase();
    }

    public boolean isBootstrapAdminEmail(String email) {
        if (bootstrapEmail.isBlank() || email == null || email.isBlank()) {
            return false;
        }
        return bootstrapEmail.equals(email.trim().toLowerCase());
    }

    public Role roleForEmail(String email) {
        return isBootstrapAdminEmail(email) ? Role.ROLE_ADMIN : Role.ROLE_USER;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapEmail.isBlank()) {
            return;
        }
        userRepository.findByEmail(bootstrapEmail).ifPresent(user -> {
            if (user.getRole() == Role.ROLE_ADMIN) {
                return;
            }
            user.setRole(Role.ROLE_ADMIN);
            userRepository.save(user);
            log.info("Compte promu ROLE_ADMIN (inbox contact) email={}", user.getEmail());
        });
    }
}
