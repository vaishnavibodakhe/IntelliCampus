package com.intellicampus.auth.config;

import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SuperAdminSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SuperAdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String superadminPassword;

    public SuperAdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${SUPERADMIN_PASSWORD:${app.security.superadmin-password:}}") String superadminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.superadminPassword = superadminPassword;
    }

    @Override
    public void run(String... args) {
        if (superadminPassword == null || superadminPassword.trim().isEmpty()) {
            logger.warn("SUPERADMIN_PASSWORD environment variable is missing; skipping default superadmin user seeding.");
            return;
        }

        String adminEmail = "admin@intellicampus.edu";
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = new User(
                    "ADM-SUPER-001",
                    Role.ADMIN,
                    "System SuperAdmin",
                    adminEmail,
                    passwordEncoder.encode(superadminPassword.trim()),
                    null
            );
            admin.setStatus(UserStatus.ACTIVE);
            admin.setMustChangePassword(false);
            admin.setFailedAttempts(0);
            userRepository.save(admin);
            logger.info("Successfully seeded default SuperAdmin account ({}) with status ACTIVE.", adminEmail);
        }
    }
}
