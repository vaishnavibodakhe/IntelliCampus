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
    @DisplayName("Staff Registration & Provisioning Tests")
    class StaffRegistrationTests {

        @Test
        @DisplayName("Bootstraps initial Principal when no principal exists in users table")
        void testBootstrapAdminSuccess() {
            StaffRegisterRequestDto req = new StaffRegisterRequestDto(
                    "Super Admin",
                    "superadmin@intellicampus.edu",
                    "SuperSecret123!",
                    Role.PRINCIPAL,
                    "Central Administration",
                    "EMP-001"
            );

            when(userRepository.countByRole(Role.PRINCIPAL)).thenReturn(0L);
            when(userRepository.existsByEmail("superadmin@intellicampus.edu")).thenReturn(false);
            when(departmentRepository.findByName("Central Administration")).thenReturn(Optional.of(csDept));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return u;
            });

            PendingStaffApprovalDto result = staffAuthService.registerStaff(req);

            assertNotNull(result);
            assertEquals(UserStatus.ACTIVE, result.getStatus());
            assertEquals(Role.PRINCIPAL, result.getRole());
            assertEquals("superadmin@intellicampus.edu", result.getEmail());
        }

        @Test
        @DisplayName("Rejects public Principal registration when a Principal already exists (403 Forbidden)")
        void testRegisterAdminRejectedWhenAdminAlreadyExists() {
            StaffRegisterRequestDto req = new StaffRegisterRequestDto(
                    "Second Admin",
                    "second.admin@intellicampus.edu",
                    "SuperSecret123!",
                    Role.PRINCIPAL,
                    null,
                    null
            );

            when(userRepository.countByRole(Role.PRINCIPAL)).thenReturn(1L);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    staffAuthService.registerStaff(req));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Principal registration is closed"));
        }

        @Test
        @DisplayName("Rejects public self-registration for non-admin staff roles like HOD, Faculty, Mentor (403 Forbidden)")
        void testRegisterNonAdminStaffRejected() {
            StaffRegisterRequestDto req = new StaffRegisterRequestDto(
                    "Dr. Alan Turing",
                    "alan.turing@intellicampus.edu",
                    "SuperSecret123!",
                    Role.HOD,
                    "Computer Science",
                    "EMP-1001"
            );

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    staffAuthService.registerStaff(req));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Public registration is disabled for staff roles"));
        }

        @Test
        @DisplayName("Admin creates staff account: unusable password hash, 24h setup OTP, and dispatches setup email")
        void testAdminCreateStaff() {
            User adminUser = new User("PRIN-01", Role.PRINCIPAL, "Principal Jane", "principal@intellicampus.edu", "hash", null);
            adminUser.setId(1L);

            AdminCreateStaffRequestDto req = new AdminCreateStaffRequestDto(
                    "Dr. Sunita Mehta",
                    "s.mehta@intellicampus.edu",
                    Role.MENTOR,
                    "Computer Science",
                    "EMP-500"
            );

            when(userRepository.existsByEmail("s.mehta@intellicampus.edu")).thenReturn(false);
            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));
            when(passwordEncoder.encode(anyString())).thenReturn("unusable_hash");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(50L);
                return u;
            });

            PendingStaffApprovalDto result = staffAuthService.createStaffByAdmin(req, adminUser);

            assertNotNull(result);
            assertEquals(Role.MENTOR, result.getRole());
            assertEquals("s.mehta@intellicampus.edu", result.getEmail());

            verify(emailService).sendStaffAccountSetupOtp(eq("s.mehta@intellicampus.edu"), anyString(), eq(Role.MENTOR));
            verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("Principal Jane"),
                    eq("STAFF_CREATED_BY_ADMIN"),
                    eq("USER"),
                    eq("50"),
                    anyString(),
                    contains("MENTOR"),
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
                    Role.PRINCIPAL,
                    null,
                    null
            );
            when(userRepository.existsByEmail("existing@intellicampus.edu")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    staffAuthService.registerStaff(req));
            assertTrue(ex.getMessage().contains("already registered"));
        }

        @Test
        @DisplayName("Admin resends staff setup OTP: invalidates previous OTP and enforces 3/hr rate limit")
        void testAdminResendStaffSetupOtp() {
            User adminUser = new User("PRIN-01", Role.PRINCIPAL, "Principal Jane", "principal@intellicampus.edu", "hash", null);
            adminUser.setId(1L);

            User staff = new User("FAC-01", Role.FACULTY, "Prof Verma", "verma@intellicampus.edu", "hash", csDept);
            staff.setId(20L);
            staff.setStatus(UserStatus.ACTIVE);

            when(userRepository.findById(20L)).thenReturn(Optional.of(staff));
            when(rateLimitRepository.countByEmailAndRequestedAtAfter(eq("verma@intellicampus.edu"), any())).thenReturn(0L);
            when(rateLimitRepository.countByIpAddressAndRequestedAtAfter(eq("127.0.0.1"), any())).thenReturn(0L);

            staffAuthService.resendStaffSetupOtp(20L, adminUser, "127.0.0.1");

            verify(passwordResetTokenRepository).invalidateAllForUser(staff);
            verify(emailService).sendStaffAccountSetupOtp(eq("verma@intellicampus.edu"), anyString(), eq(Role.FACULTY));
        }

        @Test
        @DisplayName("Admin deactivates staff account: sets DISABLED status and revokes all refresh tokens")
        void testAdminDeactivateStaff() {
            User adminUser = new User("PRIN-01", Role.PRINCIPAL, "Principal Jane", "principal@intellicampus.edu", "hash", null);
            adminUser.setId(1L);

            User staff = new User("FAC-02", Role.FACULTY, "Prof Verma", "verma@intellicampus.edu", "hash", csDept);
            staff.setId(30L);
            staff.setStatus(UserStatus.ACTIVE);

            when(userRepository.findById(30L)).thenReturn(Optional.of(staff));

            staffAuthService.deactivateStaff(30L, adminUser);

            assertEquals(UserStatus.DISABLED, staff.getStatus());
            verify(refreshTokenRepository).revokeAllForUser(staff);
            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("Principal Jane"),
                    eq("STAFF_DEACTIVATED"),
                    eq("USER"),
                    eq("30"),
                    anyString(),
                    contains("DISABLED"),
                    anyString()
            );
        }
    }

    @Nested
    @DisplayName("Staff Login & Security Rules Tests")
    class StaffLoginTests {

        @Test
        @DisplayName("Blocks login when account is DISABLED with generic 'Invalid email or password' error")
        void testLoginBlockedWhenDisabledReturnsGenericError() {
            User user = new User("HOD-03", Role.HOD, "Prof Davis", "disabled@intellicampus.edu", "hash", csDept);
            user.setStatus(UserStatus.DISABLED);

            when(userRepository.findByEmail("disabled@intellicampus.edu")).thenReturn(Optional.of(user));

            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                    staffAuthService.loginStaff(new LoginRequestDto("disabled@intellicampus.edu", "Password123")));
            assertEquals("Invalid email or password", ex.getMessage());
        }

        @Test
        @DisplayName("Role enforcement: HOD user attempting to log in selecting FACULTY is rejected with generic error")
        void testLoginBlockedWhenRoleMismatches() {
            User user = new User("HOD-04", Role.HOD, "Prof Sharma", "sharma@intellicampus.edu", "hash", csDept);
            user.setStatus(UserStatus.ACTIVE);

            when(userRepository.findByEmail("sharma@intellicampus.edu")).thenReturn(Optional.of(user));

            LoginRequestDto req = new LoginRequestDto("sharma@intellicampus.edu", "Password123", Role.FACULTY);
            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                    staffAuthService.loginStaff(req, "STAFF"));
            assertEquals("Invalid email or password", ex.getMessage());
        }

        @Test
        @DisplayName("Role enforcement: Principal user cannot log in via Staff login portal")
        void testAdminCannotLoginViaStaffPortal() {
            User admin = new User("PRIN-01", Role.PRINCIPAL, "Principal", "principal@intellicampus.edu", "hash", null);
            admin.setStatus(UserStatus.ACTIVE);

            when(userRepository.findByEmail("principal@intellicampus.edu")).thenReturn(Optional.of(admin));

            LoginRequestDto req = new LoginRequestDto("principal@intellicampus.edu", "Password123", Role.HOD);
            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                    staffAuthService.loginStaff(req, "STAFF"));
            assertEquals("Invalid email or password", ex.getMessage());
        }

        @Test
        @DisplayName("Role enforcement: Staff user cannot log in via Admin login portal")
        void testStaffCannotLoginViaAdminPortal() {
            User faculty = new User("FAC-01", Role.FACULTY, "Prof Verma", "verma@intellicampus.edu", "hash", csDept);
            faculty.setStatus(UserStatus.ACTIVE);

            when(userRepository.findByEmail("verma@intellicampus.edu")).thenReturn(Optional.of(faculty));

            LoginRequestDto req = new LoginRequestDto("verma@intellicampus.edu", "Password123");
            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                    staffAuthService.loginStaff(req, "ADMIN"));
            assertEquals("Invalid email or password", ex.getMessage());
        }

        @Test
        @DisplayName("Locks account for 15 minutes on 5 consecutive failed logins with generic error")
        void testAccountLockoutAfterFiveFailures() {
            User user = new User("HOD-04", Role.HOD, "Prof Lock", "lock@intellicampus.edu", "real_hash", csDept);
            user.setId(25L);
            user.setStatus(UserStatus.ACTIVE);
            user.setRole(Role.HOD);
            user.setFailedAttempts(4); // 5th attempt incoming

            when(userRepository.findByEmail("lock@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong_pass", "real_hash")).thenReturn(false);

            LoginRequestDto req = new LoginRequestDto("lock@intellicampus.edu", "wrong_pass", Role.HOD);
            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                    staffAuthService.loginStaff(req, "STAFF"));
            assertEquals("Invalid email or password", ex.getMessage());

            assertEquals(5, user.getFailedAttempts());
            assertEquals(UserStatus.LOCKED, user.getStatus());
            assertNotNull(user.getLockedUntil());
            assertTrue(user.getLockedUntil().isAfter(LocalDateTime.now()));
        }

        @Test
        @DisplayName("Successful login resets failed attempts and issues JWT and refresh tokens")
        void testSuccessfulLogin() {
            User user = new User("ADM-01", Role.PRINCIPAL, "Admin Jane", "admin.jane@intellicampus.edu", "hash", null);
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

            String message = staffAuthService.forgotPassword("unknown@intellicampus.edu", Role.PRINCIPAL, "127.0.0.1");
            assertEquals("If the account exists, a 6-digit password reset OTP has been sent to your registered email.", message);
            verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString());
            verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));

            // Test role mismatch (user is HOD but forgot-password is submitted for ADMIN)
            User hodUser = new User("HOD-09", Role.HOD, "Prof HOD", "hod@intellicampus.edu", "hash", csDept);
            when(userRepository.findByEmail("hod@intellicampus.edu")).thenReturn(Optional.of(hodUser));

            String messageMismatch = staffAuthService.forgotPassword("hod@intellicampus.edu", Role.PRINCIPAL, "127.0.0.1");
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
            assertTrue(ex.getReason().contains("at least 10 characters"));
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

            User adminUser = new User("ADM-01", Role.PRINCIPAL, "Super Admin", "admin@intellicampus.edu", "hash", null);
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

            User adminUser = new User("ADM-01", Role.PRINCIPAL, "Super Admin", "admin@intellicampus.edu", "hash", null);
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
