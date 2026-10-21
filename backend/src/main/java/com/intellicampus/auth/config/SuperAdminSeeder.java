package com.intellicampus.auth.config;

import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SuperAdminSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SuperAdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String adminEmail;
    private final String superadminPassword;

    public SuperAdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment environment,
            @Value("${ADMIN_EMAIL:${app.security.admin-email:admin@intellicampus.edu}}") String adminEmail,
            @Value("${SUPERADMIN_PASSWORD:${app.security.superadmin-password:}}") String superadminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.adminEmail = adminEmail;
        this.superadminPassword = superadminPassword;
    }

    @Override
    public void run(String... args) {
        // Idempotency: Exactly one admin in the entire system.
        // If an ADMIN already exists, do not seed.
        if (userRepository.existsByRole(Role.ADMIN)) {
            logger.info("Admin account already exists in database; SuperAdminSeeder skipping seeding (idempotent).");
            return;
        }

        boolean isDevProfile = environment.acceptsProfiles(Profiles.of("dev", "test"));
        String trimmedPassword = (superadminPassword != null) ? superadminPassword.trim() : "";

        if (trimmedPassword.length() < 12) {
            if (!isDevProfile) {
                throw new IllegalStateException(
                        "CRITICAL SECURITY CONFIGURATION ERROR: SUPERADMIN_PASSWORD environment variable must be configured and at least 12 characters long."
                );
            }
            logger.warn("SUPERADMIN_PASSWORD is missing or shorter than 12 characters. In dev profile, seeding safe development initial admin.");
            trimmedPassword = "DevSuperAdmin!2026";
        }

        String email = (adminEmail != null && !adminEmail.trim().isEmpty())
                ? adminEmail.trim().toLowerCase()
                : "admin@intellicampus.edu";

        // Sanitize any existing non-admin record that may have claimed this email
        if (userRepository.existsByEmail(email)) {
            userRepository.findByEmail(email).ifPresent(existing -> {
                existing.setEmail("sanitized." + System.currentTimeMillis() + "." + existing.getEmail());
                userRepository.save(existing);
            });
        }

        User admin = new User(
                "ADM-SUPER-001",
                Role.ADMIN,
                "System SuperAdmin",
                email,
                passwordEncoder.encode(trimmedPassword),
                null
        );
        admin.setStatus(UserStatus.ACTIVE);
        admin.setMustChangePassword(true);
        admin.setFailedAttempts(0);
        userRepository.save(admin);
        logger.info("Successfully seeded default SuperAdmin account ({}) with status ACTIVE and must_change_password=true.", email);
    }
}
