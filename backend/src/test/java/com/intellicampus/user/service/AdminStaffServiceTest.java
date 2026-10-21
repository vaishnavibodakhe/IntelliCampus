package com.intellicampus.user.service;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.auth.repository.RefreshTokenRepository;
import com.intellicampus.auth.service.EmailService;
import com.intellicampus.user.dto.*;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminStaffServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditChainService auditChainService;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AdminStaffService adminStaffService;

    private Department csDept;
    private User adminUser;

    @BeforeEach
    void setUp() {
        adminStaffService = new AdminStaffService(
                userRepository,
                departmentRepository,
                passwordEncoder,
                auditChainService,
                emailService,
                refreshTokenRepository
        );

        csDept = new Department("CSE", "Computer Science");
        csDept.setId(1L);

        adminUser = new User("ADM-001", Role.ADMIN, "System Admin", "admin@campus.edu", "hash", null);
        adminUser.setId(1L);
    }

    @Nested
    @DisplayName("Staff Creation Tests")
    class CreateStaffTests {

        @Test
        @DisplayName("Successfully creates HOD account with 16-char temp password and must_change_password=true")
        void testCreateHodSuccess() {
            AdminStaffCreateDto req = new AdminStaffCreateDto("Dr. Turing", "turing@campus.edu", Role.HOD, "Computer Science", "EMP-101");

            when(userRepository.existsByEmailIgnoreCase("turing@campus.edu")).thenReturn(false);
            when(userRepository.existsByUserCode("EMP-101")).thenReturn(false);
            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));
            when(userRepository.findByDepartmentAndRoleAndStatus(csDept, Role.HOD, UserStatus.ACTIVE)).thenReturn(List.of());
            when(passwordEncoder.encode(anyString())).thenReturn("bcrypt_hash");
            when(emailService.isMailConfigured()).thenReturn(false); // test return temp password once

            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(10L);
                return u;
            });

            AdminStaffResponseDto resp = adminStaffService.createStaff(req, adminUser);

            assertNotNull(resp);
            assertEquals(10L, resp.getId());
            assertEquals("Dr. Turing", resp.getName());
            assertEquals("turing@campus.edu", resp.getEmail());
            assertEquals(Role.HOD, resp.getRole());
            assertEquals(UserStatus.ACTIVE, resp.getStatus());
            assertTrue(resp.getMustChangePassword());
            assertNotNull(resp.getTemporaryPassword());
            assertEquals(16, resp.getTemporaryPassword().length());

            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("System Admin"),
                    eq("STAFF_CREATED"),
                    eq("USER"),
                    eq("10"),
                    anyString(),
                    contains("turing@campus.edu"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Rejects staff creation when role is ADMIN")
        void testCreateStaffWithAdminRoleRejected() {
            AdminStaffCreateDto req = new AdminStaffCreateDto("Fake Admin", "fake@campus.edu", Role.ADMIN, "CSE", "EMP-999");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    adminStaffService.createStaff(req, adminUser));
            assertTrue(ex.getReason().contains("Role must be HOD, FACULTY, or MENTOR"));
        }

        @Test
        @DisplayName("Rejects staff creation when role is STUDENT")
        void testCreateStaffWithStudentRoleRejected() {
            AdminStaffCreateDto req = new AdminStaffCreateDto("Fake Student", "fake@campus.edu", Role.STUDENT, "CSE", "EMP-999");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    adminStaffService.createStaff(req, adminUser));
            assertTrue(ex.getReason().contains("Role must be HOD, FACULTY, or MENTOR"));
        }

        @Test
        @DisplayName("Rejects duplicate email with 409 Conflict")
        void testCreateStaffDuplicateEmailRejected() {
            AdminStaffCreateDto req = new AdminStaffCreateDto("Dr. Turing", "existing@campus.edu", Role.FACULTY, "CSE", "EMP-102");

            when(userRepository.existsByEmailIgnoreCase("existing@campus.edu")).thenReturn(true);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    adminStaffService.createStaff(req, adminUser));
            assertTrue(ex.getReason().contains("Email is already registered"));
        }

        @Test
        @DisplayName("Rejects duplicate employee code with 409 Conflict")
        void testCreateStaffDuplicateEmployeeCodeRejected() {
            AdminStaffCreateDto req = new AdminStaffCreateDto("Dr. Turing", "turing@campus.edu", Role.FACULTY, "CSE", "EMP-EXISTING");

            when(userRepository.existsByEmailIgnoreCase("turing@campus.edu")).thenReturn(false);
            when(userRepository.existsByUserCode("EMP-EXISTING")).thenReturn(true);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    adminStaffService.createStaff(req, adminUser));
            assertTrue(ex.getReason().contains("Employee code is already registered"));
        }

        @Test
        @DisplayName("Rejects second active HOD in same department (one HOD per department constraint)")
        void testOneHodPerDepartmentEnforced() {
            AdminStaffCreateDto req = new AdminStaffCreateDto("Second HOD", "second.hod@campus.edu", Role.HOD, "Computer Science", "EMP-202");

            when(userRepository.existsByEmailIgnoreCase("second.hod@campus.edu")).thenReturn(false);
            when(userRepository.existsByUserCode("EMP-202")).thenReturn(false);
            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));

            User existingHod = new User("HOD-01", Role.HOD, "Existing HOD", "first.hod@campus.edu", "hash", csDept);
            existingHod.setId(5L);
            when(userRepository.findByDepartmentAndRoleAndStatus(csDept, Role.HOD, UserStatus.ACTIVE))
                    .thenReturn(List.of(existingHod));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    adminStaffService.createStaff(req, adminUser));
            assertTrue(ex.getReason().contains("already has an active HOD"));
        }
    }

    @Nested
    @DisplayName("Staff Status and Deletion Tests")
    class StatusAndDeletionTests {

        @Test
        @DisplayName("Updating status to DISABLED revokes refresh tokens and records audit log")
        void testUpdateStatusToDisabled() {
            User staff = new User("FAC-10", Role.FACULTY, "Prof Smith", "smith@campus.edu", "hash", csDept);
            staff.setId(10L);
            staff.setStatus(UserStatus.ACTIVE);

            when(userRepository.findById(10L)).thenReturn(Optional.of(staff));
            when(userRepository.save(any(User.class))).thenReturn(staff);

            AdminStaffStatusDto statusDto = new AdminStaffStatusDto(UserStatus.DISABLED);
            AdminStaffResponseDto result = adminStaffService.updateStaffStatus(10L, statusDto, adminUser);

            assertEquals(UserStatus.DISABLED, result.getStatus());
            verify(refreshTokenRepository).revokeAllForUser(staff);
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("System Admin"),
                    eq("STAFF_DEACTIVATED"),
                    eq("USER"),
                    eq("10"),
                    contains("ACTIVE"),
                    contains("DISABLED"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Cannot modify status of ADMIN account via staff API")
        void testCannotAlterAdminStatus() {
            User admin = new User("ADM-01", Role.ADMIN, "Super Admin", "super@campus.edu", "hash", null);
            admin.setId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            AdminStaffStatusDto statusDto = new AdminStaffStatusDto(UserStatus.DISABLED);
            assertThrows(ResponseStatusException.class, () ->
                    adminStaffService.updateStaffStatus(1L, statusDto, adminUser));
        }

        @Test
        @DisplayName("Soft-deleting staff sets status to DISABLED, revokes tokens, and logs audit")
        void testSoftDeleteStaff() {
            User staff = new User("MEN-15", Role.MENTOR, "Prof Mentor", "mentor@campus.edu", "hash", csDept);
            staff.setId(15L);
            staff.setStatus(UserStatus.ACTIVE);

            when(userRepository.findById(15L)).thenReturn(Optional.of(staff));
            when(userRepository.save(any(User.class))).thenReturn(staff);

            adminStaffService.deleteStaff(15L, adminUser);

            assertEquals(UserStatus.DISABLED, staff.getStatus());
            verify(refreshTokenRepository).revokeAllForUser(staff);
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("System Admin"),
                    eq("STAFF_DELETED"),
                    eq("USER"),
                    eq("15"),
                    anyString(),
                    contains("DISABLED"),
                    anyString()
            );
        }
    }

    @Nested
    @DisplayName("Password Reset Tests")
    class PasswordResetTests {

        @Test
        @DisplayName("Resetting password generates 16-char temp password, sets mustChangePassword=true, and revokes tokens")
        void testResetStaffPassword() {
            User staff = new User("FAC-20", Role.FACULTY, "Prof Reset", "reset@campus.edu", "old_hash", csDept);
            staff.setId(20L);
            staff.setStatus(UserStatus.ACTIVE);
            staff.setMustChangePassword(false);
            staff.setFailedAttempts(3);

            when(userRepository.findById(20L)).thenReturn(Optional.of(staff));
            when(passwordEncoder.encode(anyString())).thenReturn("new_bcrypt_hash");
            when(emailService.isMailConfigured()).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(staff);

            AdminStaffResponseDto result = adminStaffService.resetStaffPassword(20L, adminUser);

            assertNotNull(result);
            assertTrue(staff.getMustChangePassword());
            assertEquals(0, staff.getFailedAttempts());
            assertNotNull(result.getTemporaryPassword());
            assertEquals(16, result.getTemporaryPassword().length());

            verify(refreshTokenRepository).revokeAllForUser(staff);
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("System Admin"),
                    eq("PASSWORD_RESET_BY_ADMIN"),
                    eq("USER"),
                    eq("20"),
                    anyString(),
                    anyString(),
                    anyString()
            );
        }

        @Test
        @DisplayName("Cannot reset password of ADMIN account via staff API")
        void testCannotResetAdminPassword() {
            User admin = new User("ADM-01", Role.ADMIN, "Super Admin", "super@campus.edu", "hash", null);
            admin.setId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            assertThrows(ResponseStatusException.class, () ->
                    adminStaffService.resetStaffPassword(1L, adminUser));
        }
    }
}
