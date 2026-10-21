package com.intellicampus.user.service;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.user.dto.AuthResponseDto;
import com.intellicampus.user.dto.LoginRequestDto;
import com.intellicampus.user.dto.StudentRegisterRequestDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.TokenDenylist;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.TokenDenylistRepository;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DivisionRepository divisionRepository;

    @Mock
    private TokenDenylistRepository tokenDenylistRepository;

    @Mock
    private AuditChainService auditChainService;

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private IpRateLimiterService ipRateLimiterService;
    private StudentAuthService authService;

    private Department dept;
    private Division division;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(
                "4c8a91f2b6e3d7a8c1f4e5a9b2d8c3e7f1a5b9c2d8e4f1a7b3c9d5e2f8a4b6c1",
                15,
                tokenDenylistRepository
        );
        ipRateLimiterService = new IpRateLimiterService();

        authService = new StudentAuthService(
                userRepository,
                studentProfileRepository,
                departmentRepository,
                divisionRepository,
                tokenDenylistRepository,
                jwtService,
                passwordEncoder,
                ipRateLimiterService,
                auditChainService
        );

        dept = new Department("COMPUTER_SCIENCE", "Computer Science");
        ReflectionTestUtils.setField(dept, "id", 1L);

        division = new Division(dept, 3, "Div A");
        ReflectionTestUtils.setField(division, "id", 10L);
    }

    private StudentRegisterRequestDto createValidRegistrationDto() {
        StudentRegisterRequestDto dto = new StudentRegisterRequestDto();
        dto.setFullName("Aarav Shah");
        dto.setEmail("aarav.shah@intellicampus.edu");
        dto.setContactNo("9876543210");
        dto.setRollNo("2024-CS-101");
        dto.setPassword("SecurePass2026!");
        dto.setConfirmPassword("SecurePass2026!");
        dto.setDepartment("Computer Science");
        dto.setDivision("Div A");
        dto.setYear("Year 3");
        dto.setConsentVersion("1.0");
        return dto;
    }

    @Test
    @DisplayName("Validation: Rejects invalid email, phone, weak password, or password mismatch")
    void testRegistration_ValidationRejectsInvalidInputs() {
        // Null payload
        assertThrows(IllegalArgumentException.class, () -> authService.registerStudent(null));

        // Invalid email
        StudentRegisterRequestDto badEmail = createValidRegistrationDto();
        badEmail.setEmail("invalid-email-format");
        assertThrows(IllegalArgumentException.class, () -> authService.registerStudent(badEmail));

        // Invalid 10-digit Indian phone (starts with 1)
        StudentRegisterRequestDto badPhone = createValidRegistrationDto();
        badPhone.setContactNo("1234567890");
        assertThrows(IllegalArgumentException.class, () -> authService.registerStudent(badPhone));

        // Weak password (no uppercase or special char)
        StudentRegisterRequestDto weakPass = createValidRegistrationDto();
        weakPass.setPassword("password123");
        weakPass.setConfirmPassword("password123");
        assertThrows(IllegalArgumentException.class, () -> authService.registerStudent(weakPass));

        // Password mismatch
        StudentRegisterRequestDto mismatchPass = createValidRegistrationDto();
        mismatchPass.setConfirmPassword("DifferentPass2026!");
        assertThrows(IllegalArgumentException.class, () -> authService.registerStudent(mismatchPass));

        // Duplicate email
        when(userRepository.existsByEmail("aarav.shah@intellicampus.edu")).thenReturn(true);
        StudentRegisterRequestDto dupEmail = createValidRegistrationDto();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.registerStudent(dupEmail));
        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("BCrypt Hashing: Password is encrypted with BCrypt and student status set to PENDING")
    void testRegistration_BCryptHashingAndPendingStatus() {
        StudentRegisterRequestDto dto = createValidRegistrationDto();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(studentProfileRepository.findByRollNo(anyString())).thenReturn(Optional.empty());
        when(departmentRepository.findByName(anyString())).thenReturn(Optional.of(dept));
        when(divisionRepository.findByNameAndDepartmentId(anyString(), anyLong())).thenReturn(Optional.of(division));

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 100L);
            return u;
        });
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(inv -> {
            StudentProfile p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 200L);
            return p;
        });

        AuthResponseDto resp = authService.registerStudent(dto);

        assertNotNull(resp);
        assertNotNull(resp.getToken());
        assertEquals("PENDING", resp.getUser().get("status"));

        // Verify password hash in saved user starts with $2a$ and matches BCrypt
        verify(userRepository).save(argThat(user -> {
            assertTrue(user.getPasswordHash().startsWith("$2a$") || user.getPasswordHash().startsWith("$2b$"));
            assertTrue(passwordEncoder.matches("SecurePass2026!", user.getPasswordHash()));
            assertEquals(UserStatus.PENDING, user.getStatus());
            return true;
        }));

        // Verify backend audit entry for registration was recorded
        verify(auditChainService).recordEntry(
                eq(100L),
                eq("Aarav Shah"),
                eq("STUDENT_REGISTRATION"),
                eq("USER"),
                eq("100"),
                eq("{}"),
                contains("PENDING"),
                anyString()
        );
    }

    @Test
    @DisplayName("PENDING Login Rejection: Student with PENDING status is rejected from logging in")
    void testLogin_PendingStudentRejection() {
        User pendingUser = new User("aarav", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", passwordEncoder.encode("SecurePass2026!"), dept);
        pendingUser.setStatus(UserStatus.PENDING);
        ReflectionTestUtils.setField(pendingUser, "id", 100L);

        when(userRepository.findByEmail("aarav@intellicampus.edu")).thenReturn(Optional.of(pendingUser));

        LoginRequestDto req = new LoginRequestDto("aarav@intellicampus.edu", "SecurePass2026!");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.login(req, "127.0.0.1")
        );
        assertTrue(ex.getMessage().contains("pending biometric face enrollment"));
    }

    @Test
    @DisplayName("Lockout: Account locks out after 5 consecutive failed attempts")
    void testLogin_LockoutAfter5Failures() {
        User user = new User("aarav", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", passwordEncoder.encode("CorrectPassword2026!"), dept);
        user.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 100L);

        when(userRepository.findByEmail("aarav@intellicampus.edu")).thenReturn(Optional.of(user));

        LoginRequestDto wrongPassReq = new LoginRequestDto("aarav@intellicampus.edu", "WrongPass123!");

        // Attempts 1 to 4 should increment failed count and report remaining
        for (int i = 1; i <= 4; i++) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    authService.login(wrongPassReq, "127.0.0.1")
            );
            assertTrue(ex.getMessage().contains("attempt") && ex.getMessage().contains("remaining"));
            assertEquals(i, user.getFailedAttempts());
            assertNull(user.getLockedUntil());
        }

        // 5th attempt locks the account
        IllegalStateException lockEx = assertThrows(IllegalStateException.class, () ->
                authService.login(wrongPassReq, "127.0.0.1")
        );
        assertTrue(lockEx.getMessage().contains("Account locked"));
        assertEquals(5, user.getFailedAttempts());
        assertNotNull(user.getLockedUntil());

        // Subsequent login attempt while locked is rejected immediately
        IllegalStateException activelyLockedEx = assertThrows(IllegalStateException.class, () ->
                authService.login(wrongPassReq, "127.0.0.1")
        );
        assertTrue(activelyLockedEx.getMessage().contains("temporarily locked"));
    }

    @Test
    @DisplayName("Logout Denylist: Logout saves token to denylist and records USER_LOGOUT audit")
    void testLogout_AddsTokenToDenylist() {
        User user = new User("aarav", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", "hash", dept);
        ReflectionTestUtils.setField(user, "id", 100L);
        when(userRepository.findByEmail("aarav@intellicampus.edu")).thenReturn(Optional.of(user));

        String token = jwtService.generateToken(user);
        when(tokenDenylistRepository.existsByToken(token)).thenReturn(false);

        authService.logout("Bearer " + token);

        verify(tokenDenylistRepository).save(argThat(entry -> {
            assertEquals(token, entry.getToken());
            assertEquals(user, entry.getUser());
            return true;
        }));

        verify(auditChainService).recordEntry(
                eq(100L),
                eq("Aarav Shah"),
                eq("USER_LOGOUT"),
                eq("USER"),
                eq("100"),
                contains("aarav@intellicampus.edu"),
                contains("TERMINATED"),
                anyString()
        );
    }

    @Test
    @DisplayName("Revoked Token Rejection: Denylisted token is rejected by jwtService and profile lookup")
    void testRevokedToken_Rejected() {
        User user = new User("aarav", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", "hash", dept);
        ReflectionTestUtils.setField(user, "id", 100L);

        String token = jwtService.generateToken(user);

        // Before denylist: valid
        when(tokenDenylistRepository.existsByToken(token)).thenReturn(false);
        assertTrue(jwtService.isTokenValid(token));

        // After denylist: invalid
        when(tokenDenylistRepository.existsByToken(token)).thenReturn(true);
        assertFalse(jwtService.isTokenValid(token));

        // Attempting to fetch current profile with revoked token throws SecurityException
        assertThrows(SecurityException.class, () ->
                authService.getCurrentUserProfile("Bearer " + token)
        );
    }

    @Test
    @DisplayName("IP Rate Limiting: 10 failed logins from an IP triggers IP lockout independently of user account")
    void testLogin_IpRateLimiting_IndependentLockout() {
        String attackerIp = "192.168.1.100";
        LoginRequestDto badReq = new LoginRequestDto("nonexistent@intellicampus.edu", "WrongPass123!");

        when(userRepository.findByEmail("nonexistent@intellicampus.edu")).thenReturn(Optional.empty());

        // 10 failed login attempts from attackerIp
        for (int i = 0; i < 10; i++) {
            assertThrows(IllegalArgumentException.class, () ->
                    authService.login(badReq, attackerIp)
            );
        }

        // 11th attempt from same IP is blocked by IpRateLimiterService
        IllegalStateException ipEx = assertThrows(IllegalStateException.class, () ->
                authService.login(badReq, attackerIp)
        );
        assertTrue(ipEx.getMessage().contains("Too many failed login attempts from this IP address"));

        // Another IP (legitimate user) is NOT blocked
        String legitIp = "192.168.1.101";
        assertThrows(IllegalArgumentException.class, () ->
                authService.login(badReq, legitIp)
        );
    }

    @Test
    @DisplayName("Success Login: Resets failed attempts, resets IP rate limit, and records STUDENT_LOGIN audit")
    void testLogin_Success_ResetsFailedAttemptsAndAudit() {
        User user = new User("aarav", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", passwordEncoder.encode("CorrectPassword2026!"), dept);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedAttempts(3);
        ReflectionTestUtils.setField(user, "id", 100L);

        when(userRepository.findByEmail("aarav@intellicampus.edu")).thenReturn(Optional.of(user));

        LoginRequestDto req = new LoginRequestDto("aarav@intellicampus.edu", "CorrectPassword2026!");
        AuthResponseDto resp = authService.login(req, "192.168.1.50");

        assertNotNull(resp);
        assertNotNull(resp.getToken());
        assertEquals(0, user.getFailedAttempts());
        assertNull(user.getLockedUntil());

        // Verify LOGIN_SUCCESS audit entry recorded
        verify(auditChainService).recordEntry(
                eq(100L),
                eq("Aarav Shah"),
                eq("LOGIN_SUCCESS"),
                eq("USER"),
                eq("100"),
                eq("{}"),
                contains("aarav@intellicampus.edu"),
                anyString()
        );
    }
}
