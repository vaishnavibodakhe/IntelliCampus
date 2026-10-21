package com.intellicampus.auth;

import com.intellicampus.auth.config.SuperAdminSeeder;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Environment environment;


    @Test
    @DisplayName("Idempotent: Seeder skips execution if an ADMIN account already exists in DB")
    void testSeederSkipsIfAdminAlreadyExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        SuperAdminSeeder seeder = new SuperAdminSeeder(
                userRepository,
                passwordEncoder,
                environment,
                "admin@intellicampus.edu",
                "SuperAdminPass123!"
        );

        seeder.run();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Initial seeding: Creates single ADMIN with status=ACTIVE and must_change_password=true")
    void testSeederProvisionsSingleAdminWhenNoneExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(userRepository.existsByEmail("admin@intellicampus.edu")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));

        SuperAdminSeeder seeder = new SuperAdminSeeder(
                userRepository,
                passwordEncoder,
                environment,
                "admin@intellicampus.edu",
                "StrongSuperAdminPass123!"
        );

        seeder.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User seeded = userCaptor.getValue();
        assertEquals(Role.ADMIN, seeded.getRole());
        assertEquals("admin@intellicampus.edu", seeded.getEmail());
        assertEquals(UserStatus.ACTIVE, seeded.getStatus());
        assertTrue(seeded.getMustChangePassword(), "Seeded admin must have must_change_password=true");
        assertEquals(0, seeded.getFailedAttempts());
    }

    @Test
    @DisplayName("Fail startup outside dev/test if SUPERADMIN_PASSWORD is missing or shorter than 12 chars")
    void testSeederFailsStartupOutsideDevWhenPasswordWeak() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        // Simulate production profile (not dev or test)
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        SuperAdminSeeder seederWithShortPass = new SuperAdminSeeder(
                userRepository,
                passwordEncoder,
                environment,
                "admin@intellicampus.edu",
                "Short123!" // 9 chars
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, seederWithShortPass::run);
        assertTrue(ex.getMessage().contains("SUPERADMIN_PASSWORD"));
    }
}
