package com.intellicampus.auth;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.auth.dto.*;
import com.intellicampus.auth.model.PasswordResetRateLimit;
import com.intellicampus.user.model.PasswordResetToken;
import com.intellicampus.auth.model.RefreshToken;
import com.intellicampus.auth.repository.PasswordResetRateLimitRepository;
import com.intellicampus.user.repository.PasswordResetTokenRepository;
import com.intellicampus.auth.repository.RefreshTokenRepository;
import com.intellicampus.auth.service.EmailService;
import com.intellicampus.auth.service.JwtTokenProvider;
import com.intellicampus.auth.service.StaffAuthService;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.UserRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffAuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordResetRateLimitRepository rateLimitRepository;
    @Mock
    private AuditChainService auditChainService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private StaffAuthService staffAuthService;

    private Department csDept;

    @BeforeEach
    void setUp() {
        csDept = new Department("CS", "Computer Science");
        csDept.setId(1L);
    }

    @Nested
    @DisplayName("Staff Registration Tests")
    class StaffRegistrationTests {

        @Test
        @DisplayName("Registers staff with PENDING_APPROVAL status and audits event")
        void testRegisterStaffPendingApproval() {
            StaffRegisterRequestDto req = new StaffRegisterRequestDto(
                    "Dr. Alan Turing",
                    "alan.turing@intellicampus.edu",
                    "SuperSecret123!",
                    Role.HOD,
                    "Computer Science",
                    "EMP-1001"
            );

            when(userRepository.existsByEmail("alan.turing@intellicampus.edu")).thenReturn(false);
            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(10L);
                return u;
            });

            PendingStaffApprovalDto result = staffAuthService.registerStaff(req);

            assertNotNull(result);
            assertEquals(UserStatus.ACTIVE, result.getStatus());
            assertEquals(Role.HOD, result.getRole());
            assertEquals("alan.turing@intellicampus.edu", result.getEmail());

            verify(auditChainService).recordEntry(
                    eq(10L),
                    eq("Dr. Alan Turing"),
                    eq("STAFF_REGISTERED"),
                    eq("USER"),
                    eq("10"),
                    anyString(),
                    contains("ACTIVE"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Rejects registration if email already exists")
        void testRegisterStaffDuplicateEmail() {
            StaffRegisterRequestDto req = new StaffRegisterRequestDto(
                    "Duplicate User",
                    "existing@intellicampus.edu",
                    "Pass123!",
                    Role.ADMIN,
                    null,
                    null
            );
            when(userRepository.existsByEmail("existing@intellicampus.edu")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    staffAuthService.registerStaff(req));
            assertTrue(ex.getMessage().contains("already registered"));
        }
    }

    @Nested
    @DisplayName("Staff Login & Security Rules Tests")
    class StaffLoginTests {

        @Test
        @DisplayName("Blocks login when account is PENDING_APPROVAL")
        void testLoginBlockedWhenPendingApproval() {
            User user = new User("HOD-01", Role.HOD, "Prof Davis", "davis@intellicampus.edu", "hash", csDept);
            user.setStatus(UserStatus.PENDING_APPROVAL);

            when(userRepository.findByEmail("davis@intellicampus.edu")).thenReturn(Optional.of(user));

            DisabledException ex = assertThrows(DisabledException.class, () ->
                    staffAuthService.loginStaff(new LoginRequestDto("davis@intellicampus.edu", "Password123")));
            assertTrue(ex.getMessage().contains("pending administrator approval"));
        }

        @Test
        @DisplayName("Blocks login when account is REJECTED")
        void testLoginBlockedWhenRejected() {
            User user = new User("HOD-02", Role.HOD, "Prof Davis", "rejected@intellicampus.edu", "hash", csDept);
            user.setStatus(UserStatus.REJECTED);

            when(userRepository.findByEmail("rejected@intellicampus.edu")).thenReturn(Optional.of(user));

            DisabledException ex = assertThrows(DisabledException.class, () ->
                    staffAuthService.loginStaff(new LoginRequestDto("rejected@intellicampus.edu", "Password123")));
            assertTrue(ex.getMessage().contains("rejected"));
        }

        @Test
        @DisplayName("Blocks login when account is DISABLED")
        void testLoginBlockedWhenDisabled() {
            User user = new User("HOD-03", Role.HOD, "Prof Davis", "disabled@intellicampus.edu", "hash", csDept);
            user.setStatus(UserStatus.DISABLED);

            when(userRepository.findByEmail("disabled@intellicampus.edu")).thenReturn(Optional.of(user));

            DisabledException ex = assertThrows(DisabledException.class, () ->
                    staffAuthService.loginStaff(new LoginRequestDto("disabled@intellicampus.edu", "Password123")));
            assertTrue(ex.getMessage().contains("disabled"));
        }

        @Test
        @DisplayName("Locks account for 15 minutes on 5 consecutive failed logins")
        void testAccountLockoutAfterFiveFailures() {
            User user = new User("HOD-04", Role.HOD, "Prof Lock", "lock@intellicampus.edu", "real_hash", csDept);
            user.setId(25L);
            user.setStatus(UserStatus.ACTIVE);
            user.setFailedAttempts(4); // 5th attempt incoming

            when(userRepository.findByEmail("lock@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong_pass", "real_hash")).thenReturn(false);

            assertThrows(BadCredentialsException.class, () ->
                    staffAuthService.loginStaff(new LoginRequestDto("lock@intellicampus.edu", "wrong_pass")));

            assertEquals(5, user.getFailedAttempts());
            assertEquals(UserStatus.LOCKED, user.getStatus());
            assertNotNull(user.getLockedUntil());
            assertTrue(user.getLockedUntil().isAfter(LocalDateTime.now()));

            verify(auditChainService).recordEntry(
                    eq(25L),
                    eq("Prof Lock"),
                    eq("ACCOUNT_LOCKED"),
                    eq("USER"),
                    eq("25"),
                    anyString(),
                    contains("LOCKED"),
                    contains("15 minutes")
            );
        }

        @Test
        @DisplayName("Successful login resets failed attempts and issues JWT and refresh tokens")
        void testSuccessfulLogin() {
            User user = new User("ADM-01", Role.ADMIN, "Admin Jane", "admin.jane@intellicampus.edu", "hash", null);
            user.setId(1L);
            user.setStatus(UserStatus.ACTIVE);
            user.setFailedAttempts(2);

            when(userRepository.findByEmail("admin.jane@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("validPass123", "hash")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(eq(user), isNull())).thenReturn("valid.jwt.token");
            when(jwtTokenProvider.getAccessExpirationSeconds()).thenReturn(900L);

            AuthResponseDto response = staffAuthService.loginStaff(new LoginRequestDto("admin.jane@intellicampus.edu", "validPass123"));

            assertNotNull(response);
            assertEquals("valid.jwt.token", response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals(0, user.getFailedAttempts());

            verify(refreshTokenRepository).save(any(RefreshToken.class));
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("Admin Jane"),
                    eq("LOGIN_SUCCESS"),
                    eq("USER"),
                    eq("1"),
                    anyString(),
                    contains("admin.jane@intellicampus.edu"),
                    anyString()
            );
        }
    }

    @Nested
    @DisplayName("Password Reset & Rate Limiting Tests")
    class PasswordResetTests {

        @Test
        @DisplayName("Rate limits password reset requests to max 3 per hour per email")
        void testPasswordResetRateLimitEmail() {
            when(rateLimitRepository.countByEmailAndRequestedAtAfter(eq("victim@intellicampus.edu"), any(LocalDateTime.class)))
                    .thenReturn(3L);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    staffAuthService.forgotPassword("victim@intellicampus.edu", "192.168.1.5"));

            assertTrue(ex.getMessage().contains("Too many password reset requests"));
        }

        @Test
        @DisplayName("Generates 6-digit OTP, stores SHA-256 hash with 10-min expiry, and dispatches via emailService")
        void testForgotPasswordOtpGenerationAndDispatch() {
            User user = new User("HOD-05", Role.HOD, "Prof Adams", "adams@intellicampus.edu", "hash", csDept);
            user.setId(50L);
            user.setStatus(UserStatus.ACTIVE);

            when(rateLimitRepository.countByEmailAndRequestedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
            when(rateLimitRepository.countByIpAddressAndRequestedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
            when(userRepository.findByEmail("adams@intellicampus.edu")).thenReturn(Optional.of(user));

            String message = staffAuthService.forgotPassword("adams@intellicampus.edu", Role.HOD, "192.168.1.10");

            assertEquals("If the account exists, a 6-digit password reset OTP has been sent to your registered email.", message);

            ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordResetOtp(eq("adams@intellicampus.edu"), otpCaptor.capture());
            String capturedOtp = otpCaptor.getValue();
            assertNotNull(capturedOtp);
            assertTrue(capturedOtp.matches("^\\d{6}$"), "OTP must be exactly 6 digits");

            ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordResetTokenRepository).save(tokenCaptor.capture());
            PasswordResetToken savedToken = tokenCaptor.getValue();
            assertEquals(DigestUtils.sha256Hex(capturedOtp), savedToken.getTokenHash(), "Stored hash must match SHA-256 of raw OTP");
            assertFalse(savedToken.getUsed());
            assertEquals(0, savedToken.getAttempts());
            assertTrue(savedToken.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));

            verify(auditChainService).recordEntry(
                    eq(50L),
                    eq("Prof Adams"),
                    eq("PASSWORD_RESET_OTP_REQUESTED"),
                    eq("USER"),
                    eq("50"),
                    anyString(),
                    contains("adams@intellicampus.edu"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Returns generic message and dispatches no email when account is unknown or role does not match")
        void testForgotPasswordUnknownAccountOrRoleMismatch() {
            when(rateLimitRepository.countByEmailAndRequestedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
            when(rateLimitRepository.countByIpAddressAndRequestedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(0L);
            when(userRepository.findByEmail("unknown@intellicampus.edu")).thenReturn(Optional.empty());

            String message = staffAuthService.forgotPassword("unknown@intellicampus.edu", Role.ADMIN, "127.0.0.1");
            assertEquals("If the account exists, a 6-digit password reset OTP has been sent to your registered email.", message);
            verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString());
            verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));

            // Test role mismatch (user is HOD but forgot-password is submitted for ADMIN)
            User hodUser = new User("HOD-09", Role.HOD, "Prof HOD", "hod@intellicampus.edu", "hash", csDept);
            when(userRepository.findByEmail("hod@intellicampus.edu")).thenReturn(Optional.of(hodUser));

            String messageMismatch = staffAuthService.forgotPassword("hod@intellicampus.edu", Role.ADMIN, "127.0.0.1");
            assertEquals("If the account exists, a 6-digit password reset OTP has been sent to your registered email.", messageMismatch);
            verify(emailService, never()).sendPasswordResetOtp(eq("hod@intellicampus.edu"), anyString());
        }

        @Test
        @DisplayName("Resets password successfully with valid OTP and strong new password")
        void testResetPasswordWithOtpSuccess() {
            User user = new User("HOD-06", Role.HOD, "Prof Adams", "adams@intellicampus.edu", "old_hash", csDept);
            user.setId(50L);
            user.setStatus(UserStatus.LOCKED);

            String rawOtp = "482910";
            String tokenHash = DigestUtils.sha256Hex(rawOtp);

            PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, LocalDateTime.now().plusMinutes(10));
            when(userRepository.findByEmail("adams@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user))
                    .thenReturn(Optional.of(resetToken));
            when(passwordEncoder.encode("SecurePass2026!")).thenReturn("new_bcrypt_hash_12");

            ResetPasswordRequestDto req = new ResetPasswordRequestDto("adams@intellicampus.edu", rawOtp, "SecurePass2026!", "SecurePass2026!");
            staffAuthService.resetPasswordWithOtp(req);

            assertTrue(resetToken.getUsed(), "Token must be marked used");
            assertEquals("new_bcrypt_hash_12", user.getPasswordHash());
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            assertEquals(0, user.getFailedAttempts());

            verify(refreshTokenRepository).revokeAllForUser(user);
            verify(auditChainService).recordEntry(
                    eq(50L),
                    eq("Prof Adams"),
                    eq("PASSWORD_RESET_COMPLETED"),
                    eq("USER"),
                    eq("50"),
                    anyString(),
                    anyString(),
                    anyString()
            );
        }

        @Test
        @DisplayName("Wrong OTP increments attempts counter and throws BAD_REQUEST with remaining attempts")
        void testResetPasswordWrongOtpIncrementsAttempts() {
            User user = new User("HOD-07", Role.HOD, "Prof Baker", "baker@intellicampus.edu", "old_hash", csDept);
            String correctOtp = "123456";
            String wrongOtp = "999999";
            String tokenHash = DigestUtils.sha256Hex(correctOtp);

            PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, LocalDateTime.now().plusMinutes(10));
            resetToken.setAttempts(1);

            when(userRepository.findByEmail("baker@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user))
                    .thenReturn(Optional.of(resetToken));

            ResetPasswordRequestDto req = new ResetPasswordRequestDto("baker@intellicampus.edu", wrongOtp, "ValidPass123!", "ValidPass123!");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    staffAuthService.resetPasswordWithOtp(req));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Invalid OTP. 3 attempt(s) remaining"));
            assertEquals(2, resetToken.getAttempts());
            assertFalse(resetToken.getUsed());
            verify(passwordResetTokenRepository).save(resetToken);
        }

        @Test
        @DisplayName("Exceeding 5 wrong OTP attempts invalidates token and returns 429 TOO_MANY_REQUESTS")
        void testResetPasswordMaxAttemptsLimit() {
            User user = new User("HOD-08", Role.HOD, "Prof Clark", "clark@intellicampus.edu", "old_hash", csDept);
            String correctOtp = "123456";
            String wrongOtp = "000000";
            String tokenHash = DigestUtils.sha256Hex(correctOtp);

            PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, LocalDateTime.now().plusMinutes(10));
            resetToken.setAttempts(4); // 5th attempt will exceed limit

            when(userRepository.findByEmail("clark@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user))
                    .thenReturn(Optional.of(resetToken));

            ResetPasswordRequestDto req = new ResetPasswordRequestDto("clark@intellicampus.edu", wrongOtp, "ValidPass123!", "ValidPass123!");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    staffAuthService.resetPasswordWithOtp(req));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Too many incorrect attempts"));
            assertEquals(5, resetToken.getAttempts());
            assertTrue(resetToken.getUsed(), "Token must be invalidated on 5th failure");
            verify(passwordResetTokenRepository).save(resetToken);
        }

        @Test
        @DisplayName("Expired OTP returns 400 error and marks token used")
        void testResetPasswordExpiredOtp() {
            User user = new User("HOD-09", Role.HOD, "Prof Davis", "davis@intellicampus.edu", "old_hash", csDept);
            String otp = "654321";
            String tokenHash = DigestUtils.sha256Hex(otp);

            PasswordResetToken expiredToken = new PasswordResetToken(user, tokenHash, LocalDateTime.now().minusMinutes(1));

            when(userRepository.findByEmail("davis@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user))
                    .thenReturn(Optional.of(expiredToken));

            ResetPasswordRequestDto req = new ResetPasswordRequestDto("davis@intellicampus.edu", otp, "ValidPass123!", "ValidPass123!");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    staffAuthService.resetPasswordWithOtp(req));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("expired"));
            assertTrue(expiredToken.getUsed());
        }

        @Test
        @DisplayName("Weak password rejects with validation error before updating database")
        void testResetPasswordWeakPasswordValidation() {
            ResetPasswordRequestDto req = new ResetPasswordRequestDto("user@intellicampus.edu", "123456", "weak", "weak");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    staffAuthService.resetPasswordWithOtp(req));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("at least 8 characters"));
        }
    }

    @Nested
    @DisplayName("Admin Approval Workflows Tests")
    class AdminApprovalTests {

        @Test
        @DisplayName("Admin approves pending staff account transitioning it to ACTIVE")
        void testAdminApprovesStaff() {
            User pendingStaff = new User("HOD-99", Role.HOD, "Dr. Banner", "banner@intellicampus.edu", "hash", csDept);
            pendingStaff.setId(99L);
            pendingStaff.setStatus(UserStatus.PENDING_APPROVAL);

            User adminUser = new User("ADM-01", Role.ADMIN, "Super Admin", "admin@intellicampus.edu", "hash", null);
            adminUser.setId(1L);

            when(userRepository.findById(99L)).thenReturn(Optional.of(pendingStaff));

            staffAuthService.approveStaff(99L, adminUser);

            assertEquals(UserStatus.ACTIVE, pendingStaff.getStatus());
            verify(userRepository).save(pendingStaff);
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("Super Admin"),
                    eq("STAFF_APPROVED"),
                    eq("USER"),
                    eq("99"),
                    contains("PENDING_APPROVAL"),
                    contains("ACTIVE"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Admin rejects pending staff account transitioning it to REJECTED")
        void testAdminRejectsStaff() {
            User pendingStaff = new User("HOD-98", Role.HOD, "Dr. Strange", "strange@intellicampus.edu", "hash", csDept);
            pendingStaff.setId(98L);
            pendingStaff.setStatus(UserStatus.PENDING_APPROVAL);

            User adminUser = new User("ADM-01", Role.ADMIN, "Super Admin", "admin@intellicampus.edu", "hash", null);
            adminUser.setId(1L);

            when(userRepository.findById(98L)).thenReturn(Optional.of(pendingStaff));

            staffAuthService.rejectStaff(98L, "Invalid employee credentials", adminUser);

            assertEquals(UserStatus.REJECTED, pendingStaff.getStatus());
            verify(userRepository).save(pendingStaff);
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("Super Admin"),
                    eq("STAFF_REJECTED"),
                    eq("USER"),
                    eq("98"),
                    contains("PENDING_APPROVAL"),
                    contains("REJECTED"),
                    contains("Invalid employee credentials")
            );
        }
    }
}
