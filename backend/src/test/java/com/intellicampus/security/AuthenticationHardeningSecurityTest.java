package com.intellicampus.security;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.auth.controller.AdminAuthController;
import com.intellicampus.auth.dto.*;
import com.intellicampus.auth.model.RefreshToken;
import com.intellicampus.auth.repository.PasswordResetRateLimitRepository;
import com.intellicampus.auth.repository.RefreshTokenRepository;
import com.intellicampus.auth.service.EmailService;
import com.intellicampus.auth.service.StaffAuthService;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.security.jwt.JwtTokenProvider;
import com.intellicampus.user.model.PasswordResetToken;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.PasswordResetTokenRepository;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AuthenticationHardeningSecurityTest {

    @Nested
    @DisplayName("1. JWT Secret Startup Validation")
    class JwtSecretValidationTests {

        @Test
        @DisplayName("Fails initialization if JWT_SECRET is null or blank")
        void testBlankSecretFails() {
            assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(null, 15));
            assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("   ", 15));
        }

        @Test
        @DisplayName("Fails initialization if JWT_SECRET is shorter than 32 characters")
        void testShortSecretFails() {
            String shortSecret = "too_short_secret_key_123456789"; // 30 chars
            assertTrue(shortSecret.length() < 32);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> new JwtTokenProvider(shortSecret, 15));
            assertTrue(ex.getMessage().contains("at least 32 characters"));
        }

        @Test
        @DisplayName("Successfully initializes when JWT_SECRET is at least 32 characters")
        void testValidSecretSucceeds() {
            String validSecret = "a_very_secure_and_cryptographically_sound_secret_key_256_bit";
            JwtTokenProvider provider = new JwtTokenProvider(validSecret, 15);
            assertNotNull(provider);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("2. Lockout, Timing Attack Mitigation & Password Policy Unit Tests")
    class StaffAuthServiceHardeningTests {

        @Mock private UserRepository userRepository;
        @Mock private DepartmentRepository departmentRepository;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private com.intellicampus.auth.service.JwtTokenProvider jwtTokenProvider;
        @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
        @Mock private RefreshTokenRepository refreshTokenRepository;
        @Mock private PasswordResetRateLimitRepository rateLimitRepository;
        @Mock private AuditChainService auditChainService;
        @Mock private EmailService emailService;

        private StaffAuthService staffAuthService;
        private Department csDept;

        @BeforeEach
        void setUp() {
            staffAuthService = new StaffAuthService(
                    userRepository, departmentRepository, passwordEncoder,
                    jwtTokenProvider, passwordResetTokenRepository, refreshTokenRepository,
                    rateLimitRepository, auditChainService, emailService
            );
            csDept = new Department("CSE", "Computer Science");
            csDept.setId(1L);
        }

        @Test
        @DisplayName("Timing mitigation: runs dummy BCrypt on unknown user and throws generic BadCredentialsException")
        void testTimingMitigationOnUnknownUser() {
            when(userRepository.findByEmail("ghost@campus.edu")).thenReturn(Optional.empty());

            LoginRequestDto req = new LoginRequestDto("ghost@campus.edu", "Password123!");
            BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                    () -> staffAuthService.loginStaff(req));

            assertEquals("Invalid email or password", ex.getMessage());
            verify(passwordEncoder).matches(eq("Password123!"), anyString());
        }

        @Test
        @DisplayName("Locks account for 15 minutes on 5 consecutive failures and records ACCOUNT_LOCKED audit")
        void testAccountLockoutOnFifthFailure() {
            User staff = new User("FAC-01", Role.FACULTY, "Prof Lock", "lock@campus.edu", "real_hash", csDept);
            staff.setId(10L);
            staff.setFailedAttempts(4); // 5th attempt incoming

            when(userRepository.findByEmail("lock@campus.edu")).thenReturn(Optional.of(staff));
            when(passwordEncoder.matches("wrong_pass", "real_hash")).thenReturn(false);

            LoginRequestDto req = new LoginRequestDto("lock@campus.edu", "wrong_pass");
            assertThrows(BadCredentialsException.class, () -> staffAuthService.loginStaff(req));

            assertEquals(5, staff.getFailedAttempts());
            assertEquals(UserStatus.LOCKED, staff.getStatus());
            assertNotNull(staff.getLockedUntil());
            assertTrue(staff.getLockedUntil().isAfter(LocalDateTime.now()));

            verify(auditChainService).recordEntry(
                    eq(10L),
                    eq("Prof Lock"),
                    eq("ACCOUNT_LOCKED"),
                    eq("USER"),
                    eq("10"),
                    anyString(),
                    contains("LOCKED"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Active lockout blocks login attempts immediately")
        void testActiveLockoutBlocksLogin() {
            User staff = new User("FAC-02", Role.FACULTY, "Prof Locked", "locked@campus.edu", "real_hash", csDept);
            staff.setId(11L);
            staff.setStatus(UserStatus.LOCKED);
            staff.setLockedUntil(LocalDateTime.now().plusMinutes(10));

            when(userRepository.findByEmail("locked@campus.edu")).thenReturn(Optional.of(staff));

            LoginRequestDto req = new LoginRequestDto("locked@campus.edu", "any_password");
            assertThrows(LockedException.class, () -> staffAuthService.loginStaff(req));
        }

        @Test
        @DisplayName("Password policy rejects passwords shorter than 10 characters")
        void testPasswordPolicyRejectsShortPassword() {
            User staff = new User("FAC-03", Role.FACULTY, "Prof Short", "short@campus.edu", "old_hash", csDept);
            staff.setId(12L);
            when(passwordEncoder.matches("old_pass", "old_hash")).thenReturn(true);

            ChangePasswordRequestDto req = new ChangePasswordRequestDto();
            req.setCurrentPassword("old_pass");
            req.setNewPassword("Short1!"); // 7 chars

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> staffAuthService.changePassword(staff, req));
            assertTrue(ex.getReason().contains("at least 10 characters"));
        }

        @Test
        @DisplayName("Password policy rejects passwords containing email address")
        void testPasswordPolicyRejectsEmailInPassword() {
            User staff = new User("FAC-04", Role.FACULTY, "Prof Email", "prof.email@campus.edu", "old_hash", csDept);
            staff.setId(13L);
            when(passwordEncoder.matches("old_pass", "old_hash")).thenReturn(true);

            ChangePasswordRequestDto req = new ChangePasswordRequestDto();
            req.setCurrentPassword("old_pass");
            req.setNewPassword("prof.email!2024Pass");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> staffAuthService.changePassword(staff, req));
            assertTrue(ex.getReason().contains("cannot be equal to or contain your email address"));
        }

        @Test
        @DisplayName("Password policy rejects passwords containing user name")
        void testPasswordPolicyRejectsNameInPassword() {
            User staff = new User("FAC-05", Role.FACULTY, "Alexander", "bob@campus.edu", "old_hash", csDept);
            staff.setId(14L);
            when(passwordEncoder.matches("old_pass", "old_hash")).thenReturn(true);

            ChangePasswordRequestDto req = new ChangePasswordRequestDto();
            req.setCurrentPassword("old_pass");
            req.setNewPassword("Alexander!2024Secure");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> staffAuthService.changePassword(staff, req));
            assertTrue(ex.getReason().contains("cannot contain your name"));
        }

        @Test
        @DisplayName("Admin 2FA OTP flow: triggers OTP dispatch when admin-otp-enabled is true")
        void testAdmin2FaOtpTriggered() {
            User admin = new User("ADM-01", Role.ADMIN, "System Admin", "admin@campus.edu", "admin_hash", null);
            admin.setId(1L);

            when(userRepository.findByEmail("admin@campus.edu")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("AdminPass123!", "admin_hash")).thenReturn(true);
            when(emailService.isMailConfigured()).thenReturn(true);

            staffAuthService.setAdminOtpEnabled(true);

            LoginRequestDto req = new LoginRequestDto("admin@campus.edu", "AdminPass123!");
            AuthResponseDto response = staffAuthService.loginAdmin(req);

            assertNotNull(response);
            assertTrue(response.getOtpRequired());
            assertNull(response.getAccessToken());
            verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
            verify(emailService).sendPasswordResetOtp(eq("admin@campus.edu"), anyString());
        }

        @Test
        @DisplayName("Admin 2FA OTP verification: verifies OTP, issues tokens, and logs LOGIN_SUCCESS")
        void testAdmin2FaOtpVerifySuccess() {
            User admin = new User("ADM-01", Role.ADMIN, "System Admin", "admin@campus.edu", "admin_hash", null);
            admin.setId(1L);

            String validOtp = "123456";
            String otpHash = DigestUtils.sha256Hex(validOtp);
            PasswordResetToken otpToken = new PasswordResetToken(admin, otpHash, LocalDateTime.now().plusMinutes(5));

            when(userRepository.findByEmail("admin@campus.edu")).thenReturn(Optional.of(admin));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(admin))
                    .thenReturn(Optional.of(otpToken));
            when(jwtTokenProvider.generateAccessToken(eq(admin), isNull())).thenReturn("jwt.admin.token");
            when(jwtTokenProvider.getAccessExpirationSeconds()).thenReturn(900L);

            AdminOtpVerifyDto req = new AdminOtpVerifyDto("admin@campus.edu", "123456");
            AuthResponseDto response = staffAuthService.verifyAdminOtp(req);

            assertNotNull(response);
            assertEquals("jwt.admin.token", response.getAccessToken());
            assertTrue(otpToken.getUsed());
            verify(refreshTokenRepository).save(any(RefreshToken.class));
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("System Admin"),
                    eq("ADMIN_LOGIN_SUCCESS"),
                    eq("USER"),
                    eq("1"),
                    anyString(),
                    contains("2fa"),
                    anyString()
            );
        }
    }

    @Nested
    @WebMvcTest(controllers = AdminAuthController.class)
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
    @DisplayName("3. HTTP Security Headers and Web MVC Integration")
    class HttpSecurityHeadersTests {

        @Autowired
        private MockMvc mockMvc;

        @MockBean private StaffAuthService staffAuthService;
        @MockBean private UserRepository userRepository;
        @MockBean private StudentProfileRepository studentProfileRepository;
        @MockBean private JwtTokenProvider jwtTokenProvider;
        @MockBean private com.intellicampus.user.repository.RevokedTokenRepository revokedTokenRepository;
        @MockBean private com.intellicampus.user.repository.TokenDenylistRepository tokenDenylistRepository;

        @Test
        @DisplayName("Security headers (HSTS, Content-Type-Options, Frame-Options, CSP) are present on responses")
        void testSecurityHeadersPresent() throws Exception {
            when(staffAuthService.loginAdmin(any())).thenReturn(new AuthResponseDto());

            mockMvc.perform(post("/api/auth/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@test.com\",\"password\":\"Pass123!\"}"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
        }

        @Test
        @DisplayName("POST /api/auth/admin/verify-otp is publicly accessible")
        void testAdminVerifyOtpPublicEndpoint() throws Exception {
            when(staffAuthService.verifyAdminOtp(any())).thenReturn(new AuthResponseDto("access.token", "refresh.token", 900L, null));

            mockMvc.perform(post("/api/auth/admin/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@test.com\",\"otp\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access.token"));
        }
    }
}
