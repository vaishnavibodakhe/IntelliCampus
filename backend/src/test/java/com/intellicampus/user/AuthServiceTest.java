package com.intellicampus.user;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.common.exception.DuplicateResourceException;
import com.intellicampus.common.exception.InvalidCredentialsException;
import com.intellicampus.common.exception.ValidationException;
import com.intellicampus.security.jwt.JwtTokenProvider;
import com.intellicampus.security.service.EmailService;
import com.intellicampus.user.dto.*;
import com.intellicampus.user.model.PasswordResetToken;
import com.intellicampus.user.model.RevokedToken;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.PasswordResetTokenRepository;
import com.intellicampus.user.repository.RevokedTokenRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.AuthService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private RevokedTokenRepository revokedTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider = new JwtTokenProvider("unit_test_jwt_secret_key_minimum_32_chars_for_hmac_sha256", 15);
    @Mock
    private EmailService emailService;

    private AuthService authService;

    private Department dept;

    @BeforeEach
    void setUp() {
        dept = new Department("CS", "Computer Science");
        authService = new AuthService(
                userRepository,
                departmentRepository,
                passwordResetTokenRepository,
                revokedTokenRepository,
                passwordEncoder,
                tokenProvider,
                emailService
        );
        authService.clearRateLimitMap();
    }

    @Nested
    @DisplayName("Faculty Registration Tests")
    class FacultyRegistrationTests {

        @Test
        @DisplayName("Success: Registers new faculty with valid fields and unique email/facultyId")
        void registerFaculty_Success() {
            FacultyRegisterRequest req = new FacultyRegisterRequest();
            req.setFullName("Prof. Alice Smith");
            req.setFacultyId("FAC-101");
            req.setEmail("alice.smith@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9876543210");
            req.setPassword("SecurePass1@");
            req.setConfirmPassword("SecurePass1@");

            when(userRepository.existsByEmailIgnoreCase("alice.smith@intellicampus.edu")).thenReturn(false);
            when(userRepository.existsByUserCodeAndRole("FAC-101", Role.FACULTY)).thenReturn(false);
            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(dept));
            when(passwordEncoder.encode("SecurePass1@")).thenReturn("hashed_pass");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse resp = authService.registerFaculty(req);

            assertNotNull(resp);
            assertEquals("FAC-101", resp.getUserCode());
            assertEquals(Role.FACULTY, resp.getRole());
            assertNotNull(resp.getToken());
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Fails: Duplicate email throws DuplicateResourceException (HTTP 409)")
        void registerFaculty_DuplicateEmail() {
            FacultyRegisterRequest req = new FacultyRegisterRequest();
            req.setFullName("Prof. Alice Smith");
            req.setFacultyId("FAC-101");
            req.setEmail("existing@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9876543210");
            req.setPassword("SecurePass1@");
            req.setConfirmPassword("SecurePass1@");

            when(userRepository.existsByEmailIgnoreCase("existing@intellicampus.edu")).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () -> authService.registerFaculty(req));
        }

        @Test
        @DisplayName("Fails: Duplicate faculty ID throws DuplicateResourceException")
        void registerFaculty_DuplicateFacultyId() {
            FacultyRegisterRequest req = new FacultyRegisterRequest();
            req.setFullName("Prof. Alice Smith");
            req.setFacultyId("FAC-101");
            req.setEmail("alice@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9876543210");
            req.setPassword("SecurePass1@");
            req.setConfirmPassword("SecurePass1@");

            when(userRepository.existsByEmailIgnoreCase("alice@intellicampus.edu")).thenReturn(false);
            when(userRepository.existsByUserCodeAndRole("FAC-101", Role.FACULTY)).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () -> authService.registerFaculty(req));
        }

        @Test
        @DisplayName("Fails: Mismatched password and confirmPassword throws ValidationException")
        void registerFaculty_MismatchedPasswords() {
            FacultyRegisterRequest req = new FacultyRegisterRequest();
            req.setFullName("Prof. Alice Smith");
            req.setFacultyId("FAC-101");
            req.setEmail("alice@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9876543210");
            req.setPassword("SecurePass1@");
            req.setConfirmPassword("DifferentPass2#");

            assertThrows(ValidationException.class, () -> authService.registerFaculty(req));
        }

        @Test
        @DisplayName("Fails: Weak password (no digit or special char) throws ValidationException")
        void registerFaculty_WeakPassword() {
            FacultyRegisterRequest req = new FacultyRegisterRequest();
            req.setFullName("Prof. Alice Smith");
            req.setFacultyId("FAC-101");
            req.setEmail("alice@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9876543210");
            req.setPassword("weakpassword");
            req.setConfirmPassword("weakpassword");

            assertThrows(ValidationException.class, () -> authService.registerFaculty(req));
        }
    }

    @Nested
    @DisplayName("Mentor Registration Tests")
    class MentorRegistrationTests {

        @Test
        @DisplayName("Success: Registers new mentor with valid mentorId and phone")
        void registerMentor_Success() {
            MentorRegisterRequest req = new MentorRegisterRequest();
            req.setFullName("Dr. Robert Lee");
            req.setMentorId("MEN-202");
            req.setEmail("robert.lee@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9123456789");
            req.setPassword("StrongMentor2026#");
            req.setConfirmPassword("StrongMentor2026#");

            when(userRepository.existsByEmailIgnoreCase("robert.lee@intellicampus.edu")).thenReturn(false);
            when(userRepository.existsByUserCodeAndRole("MEN-202", Role.MENTOR)).thenReturn(false);
            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(dept));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse resp = authService.registerMentor(req);

            assertNotNull(resp);
            assertEquals("MEN-202", resp.getUserCode());
            assertEquals(Role.MENTOR, resp.getRole());
            assertNotNull(resp.getToken());
        }

        @Test
        @DisplayName("Fails: Invalid mentor ID pattern throws ValidationException")
        void registerMentor_InvalidIdPattern() {
            MentorRegisterRequest req = new MentorRegisterRequest();
            req.setFullName("Dr. Robert Lee");
            req.setMentorId("MNT-202"); // Does not match ^MEN-\d{3,}$
            req.setEmail("robert.lee@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9123456789");
            req.setPassword("StrongMentor2026#");
            req.setConfirmPassword("StrongMentor2026#");

            assertThrows(ValidationException.class, () -> authService.registerMentor(req));
        }

        @Test
        @DisplayName("Fails: Duplicate email throws DuplicateResourceException (409)")
        void registerMentor_DuplicateEmail() {
            MentorRegisterRequest req = new MentorRegisterRequest();
            req.setFullName("Dr. Robert Lee");
            req.setMentorId("MEN-202");
            req.setEmail("existing@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9123456789");
            req.setPassword("StrongMentor2026#");
            req.setConfirmPassword("StrongMentor2026#");

            when(userRepository.existsByEmailIgnoreCase("existing@intellicampus.edu")).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () -> authService.registerMentor(req));
        }

        @Test
        @DisplayName("Fails: Duplicate employee ID throws DuplicateResourceException (409)")
        void registerMentor_DuplicateEmployeeId() {
            MentorRegisterRequest req = new MentorRegisterRequest();
            req.setFullName("Dr. Robert Lee");
            req.setMentorId("MEN-202");
            req.setEmail("new@intellicampus.edu");
            req.setDepartment("Computer Science");
            req.setPhone("9123456789");
            req.setPassword("StrongMentor2026#");
            req.setConfirmPassword("StrongMentor2026#");

            when(userRepository.existsByEmailIgnoreCase("new@intellicampus.edu")).thenReturn(false);
            when(userRepository.existsByUserCode("MEN-202")).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () -> authService.registerMentor(req));
        }
    }

    @Nested
    @DisplayName("Login & Cross-Role Access Control Tests")
    class LoginTests {

        @Test
        @DisplayName("Success: Faculty logs in with valid credentials")
        void loginFaculty_Success() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "hashed_pwd", dept);
            when(userRepository.findByEmailIgnoreCase("v.verma@intellicampus.edu")).thenReturn(Optional.of(faculty));
            when(passwordEncoder.matches("FacultyPass1!", "hashed_pwd")).thenReturn(true);

            AuthResponse resp = authService.loginFaculty(new LoginRequest("v.verma@intellicampus.edu", "FacultyPass1!"));

            assertNotNull(resp);
            assertEquals(Role.FACULTY, resp.getRole());
            assertNotNull(resp.getToken());
        }

        @Test
        @DisplayName("Fails: Wrong password returns generic InvalidCredentialsException (HTTP 401)")
        void login_WrongPassword() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "hashed_pwd", dept);
            when(userRepository.findByEmailIgnoreCase("v.verma@intellicampus.edu")).thenReturn(Optional.of(faculty));
            when(passwordEncoder.matches("WrongPass", "hashed_pwd")).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () ->
                    authService.loginFaculty(new LoginRequest("v.verma@intellicampus.edu", "WrongPass")));
        }

        @Test
        @DisplayName("BLOCKED: Faculty account cannot log in via Mentor login endpoint (Cross-role prevention)")
        void crossRole_FacultyUsingMentorLogin_Blocked() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "hashed_pwd", dept);
            when(userRepository.findByEmailIgnoreCase("v.verma@intellicampus.edu")).thenReturn(Optional.of(faculty));

            // Calling loginMentor with a FACULTY account must throw generic InvalidCredentialsException
            assertThrows(InvalidCredentialsException.class, () ->
                    authService.loginMentor(new LoginRequest("v.verma@intellicampus.edu", "FacultyPass1!")));
        }

        @Test
        @DisplayName("BLOCKED: Mentor account cannot log in via Faculty login endpoint (Cross-role prevention)")
        void crossRole_MentorUsingFacultyLogin_Blocked() {
            User mentor = new User("MEN-201", Role.MENTOR, "Dr. Sunita Mehta", "s.mehta@intellicampus.edu", "hashed_pwd", dept);
            when(userRepository.findByEmailIgnoreCase("s.mehta@intellicampus.edu")).thenReturn(Optional.of(mentor));

            assertThrows(InvalidCredentialsException.class, () ->
                    authService.loginFaculty(new LoginRequest("s.mehta@intellicampus.edu", "MentorPass1!")));
        }
    }

    @Nested
    @DisplayName("Logout & Token Blacklist Tests")
    class LogoutTests {

        @Test
        @DisplayName("Success: Logout stores SHA-256 hash in revoked_tokens table")
        void logout_BlacklistsToken() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "hashed_pwd", dept);
            String rawToken = tokenProvider.generateToken(faculty);
            String tokenHash = DigestUtils.sha256Hex(rawToken);

            when(revokedTokenRepository.existsByTokenHash(tokenHash)).thenReturn(false);

            authService.logout("Bearer " + rawToken);

            verify(revokedTokenRepository, times(1)).save(any(RevokedToken.class));
        }
    }

    @Nested
    @DisplayName("Forgot Password & Reset Password Flow Tests")
    class PasswordResetTests {

        @BeforeEach
        void setUp() {
            authService.clearRateLimitMap();
        }

        @Test
        @DisplayName("Success: Forgot password for registered user stores SHA-256 hash and sends email")
        void forgotPassword_RegisteredUser_SendsEmail() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "hashed_pwd", dept);
            when(userRepository.findByEmailIgnoreCase("v.verma@intellicampus.edu")).thenReturn(Optional.of(faculty));

            ApiResponse resp = authService.requestForgotPassword(new ForgotPasswordRequest("v.verma@intellicampus.edu", "FACULTY"));

            assertTrue(resp.isSuccess());
            verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
        }

        @Test
        @DisplayName("Security: Forgot password for unregistered user returns identical generic success message")
        void forgotPassword_UnregisteredUser_ReturnsGenericMessage() {
            when(userRepository.findByEmailIgnoreCase("unknown@intellicampus.edu")).thenReturn(Optional.empty());

            ApiResponse resp = authService.requestForgotPassword(new ForgotPasswordRequest("unknown@intellicampus.edu", "FACULTY"));

            assertTrue(resp.isSuccess());
            verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        }

        @Test
        @DisplayName("Success: Reset password updates user password hash and marks token as used")
        void resetPassword_Success() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "old_hash", dept);
            String rawToken = "valid_reset_token_12345";
            String tokenHash = DigestUtils.sha256Hex(rawToken);

            PasswordResetToken resetToken = new PasswordResetToken(faculty, tokenHash, LocalDateTime.now().plusMinutes(15));
            when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
            when(passwordEncoder.encode("NewSecret2026@")).thenReturn("new_hash");

            ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewSecret2026@", "NewSecret2026@");
            ApiResponse resp = authService.resetPassword(req);

            assertTrue(resp.isSuccess());
            assertEquals("new_hash", faculty.getPasswordHash());
            assertTrue(resetToken.getUsed());
            verify(passwordResetTokenRepository, times(1)).invalidateAllActiveTokensForUser(faculty);
        }

        @Test
        @DisplayName("Fails: Already used token cannot be reused")
        void resetPassword_UsedToken_Fails() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "old_hash", dept);
            String rawToken = "already_used_token";
            String tokenHash = DigestUtils.sha256Hex(rawToken);

            PasswordResetToken resetToken = new PasswordResetToken(faculty, tokenHash, LocalDateTime.now().plusMinutes(15));
            resetToken.setUsed(true);
            when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

            ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewSecret2026@", "NewSecret2026@");
            assertThrows(ValidationException.class, () -> authService.resetPassword(req));
        }

        @Test
        @DisplayName("Fails: Expired token is rejected")
        void resetPassword_ExpiredToken_Fails() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "old_hash", dept);
            String rawToken = "expired_token";
            String tokenHash = DigestUtils.sha256Hex(rawToken);

            PasswordResetToken resetToken = new PasswordResetToken(faculty, tokenHash, LocalDateTime.now().minusMinutes(5));
            when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

            ResetPasswordRequest req = new ResetPasswordRequest(rawToken, "NewSecret2026@", "NewSecret2026@");
            assertThrows(ValidationException.class, () -> authService.resetPassword(req));
        }
    }

    @Nested
    @DisplayName("Mentor OTP Forgot-Password Tests")
    class MentorOtpTests {

        private User mentor;

        @BeforeEach
        void setUpMentor() {
            authService.clearRateLimitMap();
            mentor = new User("MEN-201", Role.MENTOR, "Dr. Sunita Mehta", "sunita@intellicampus.edu", "old_hash", dept);
            authService.setJwtSecret("TestSecretKeyForHmacUnitTestingOnly1234567890");
        }

        @Test
        @DisplayName("Success: Generates 6-digit OTP, stores HMAC-SHA256, and dispatches email")
        void requestMentorForgotPassword_Success() {
            ForgotPasswordRequest req = new ForgotPasswordRequest("sunita@intellicampus.edu", "MENTOR");
            when(userRepository.findByEmailIgnoreCase("sunita@intellicampus.edu")).thenReturn(Optional.of(mentor));

            ApiResponse resp = authService.requestMentorForgotPassword(req);

            assertTrue(resp.isSuccess());
            verify(passwordResetTokenRepository, times(1)).invalidateAllActiveTokensForUser(mentor);
            verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
            verify(emailService, times(1)).sendPasswordResetOtp(eq("sunita@intellicampus.edu"), anyString());
        }

        @Test
        @DisplayName("Success: Unknown email returns generic response without creating OTP")
        void requestMentorForgotPassword_UnknownEmail() {
            ForgotPasswordRequest req = new ForgotPasswordRequest("unknown@intellicampus.edu", "MENTOR");
            when(userRepository.findByEmailIgnoreCase("unknown@intellicampus.edu")).thenReturn(Optional.empty());

            ApiResponse resp = authService.requestMentorForgotPassword(req);

            assertTrue(resp.isSuccess());
            verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
            verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString());
        }

        @Test
        @DisplayName("Success: Faculty account requesting mentor OTP returns generic response without creating OTP")
        void requestMentorForgotPassword_RoleMismatch() {
            User faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "old_hash", dept);
            ForgotPasswordRequest req = new ForgotPasswordRequest("v.verma@intellicampus.edu", "MENTOR");
            when(userRepository.findByEmailIgnoreCase("v.verma@intellicampus.edu")).thenReturn(Optional.of(faculty));

            ApiResponse resp = authService.requestMentorForgotPassword(req);

            assertTrue(resp.isSuccess());
            verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
            verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString());
        }

        @Test
        @DisplayName("Success: Verifies correct 6-digit OTP and returns single-use reset token")
        void verifyMentorOtp_Correct() {
            String otp = "123456";
            String otpHmac = authService.computeHmacSha256(otp);
            PasswordResetToken token = new PasswordResetToken(mentor, otpHmac, LocalDateTime.now().plusMinutes(10));

            when(userRepository.findByEmailIgnoreCase("sunita@intellicampus.edu")).thenReturn(Optional.of(mentor));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(mentor))
                    .thenReturn(Optional.of(token));

            VerifyOtpRequest req = new VerifyOtpRequest("sunita@intellicampus.edu", otp);
            ApiResponse resp = authService.verifyMentorOtp(req);

            assertTrue(resp.isSuccess());
            assertNotNull(resp.getData());
            assertNotNull(token.getResetTokenHash());
            assertEquals("VERIFIED_" + token.getId(), token.getTokenHash());
            verify(passwordResetTokenRepository, times(1)).save(token);
        }

        @Test
        @DisplayName("Fails: Wrong OTP increments attempt counter and returns 400 Bad Request")
        void verifyMentorOtp_WrongOtp() {
            String otp = "123456";
            String otpHmac = authService.computeHmacSha256(otp);
            PasswordResetToken token = new PasswordResetToken(mentor, otpHmac, LocalDateTime.now().plusMinutes(10));

            when(userRepository.findByEmailIgnoreCase("sunita@intellicampus.edu")).thenReturn(Optional.of(mentor));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(mentor))
                    .thenReturn(Optional.of(token));

            VerifyOtpRequest req = new VerifyOtpRequest("sunita@intellicampus.edu", "999999");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.verifyMentorOtp(req));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Invalid OTP"));
            assertEquals(1, token.getAttempts());
            verify(passwordResetTokenRepository, times(1)).save(token);
        }

        @Test
        @DisplayName("Fails: Reaching 5 wrong attempts invalidates OTP and throws 429 Too Many Requests")
        void verifyMentorOtp_MaxAttempts() {
            String otp = "123456";
            String otpHmac = authService.computeHmacSha256(otp);
            PasswordResetToken token = new PasswordResetToken(mentor, otpHmac, LocalDateTime.now().plusMinutes(10));
            token.setAttempts(4);

            when(userRepository.findByEmailIgnoreCase("sunita@intellicampus.edu")).thenReturn(Optional.of(mentor));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(mentor))
                    .thenReturn(Optional.of(token));

            VerifyOtpRequest req = new VerifyOtpRequest("sunita@intellicampus.edu", "999999");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.verifyMentorOtp(req));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
            assertTrue(token.getUsed());
            verify(passwordResetTokenRepository, times(1)).save(token);
        }

        @Test
        @DisplayName("Fails: Expired OTP is marked used and throws 400 Bad Request")
        void verifyMentorOtp_Expired() {
            String otp = "123456";
            String otpHmac = authService.computeHmacSha256(otp);
            PasswordResetToken token = new PasswordResetToken(mentor, otpHmac, LocalDateTime.now().minusMinutes(1));

            when(userRepository.findByEmailIgnoreCase("sunita@intellicampus.edu")).thenReturn(Optional.of(mentor));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(mentor))
                    .thenReturn(Optional.of(token));

            VerifyOtpRequest req = new VerifyOtpRequest("sunita@intellicampus.edu", otp);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.verifyMentorOtp(req));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(token.getUsed());
            verify(passwordResetTokenRepository, times(1)).save(token);
        }

        @Test
        @DisplayName("Success: Updates password with BCrypt using verified reset token")
        void resetMentorPassword_Success() {
            String rawResetToken = "single_use_reset_token_abc123";
            String resetTokenHash = DigestUtils.sha256Hex(rawResetToken);

            PasswordResetToken token = new PasswordResetToken(mentor, "VERIFIED_1", LocalDateTime.now().plusMinutes(10));
            token.setResetTokenHash(resetTokenHash);

            when(passwordResetTokenRepository.findByResetTokenHashAndUsedFalse(resetTokenHash))
                    .thenReturn(Optional.of(token));
            when(passwordEncoder.encode("NewMentorPass2026#")).thenReturn("bcrypt_new_hash");

            ResetPasswordRequest req = new ResetPasswordRequest(rawResetToken, "NewMentorPass2026#", "NewMentorPass2026#");
            ApiResponse resp = authService.resetMentorPassword(req);

            assertTrue(resp.isSuccess());
            assertEquals("bcrypt_new_hash", mentor.getPasswordHash());
            assertTrue(token.getUsed());
            verify(passwordResetTokenRepository, times(1)).save(token);
            verify(passwordResetTokenRepository, times(1)).invalidateAllActiveTokensForUser(mentor);
            verify(userRepository, times(1)).save(mentor);
        }

        @Test
        @DisplayName("Fails: Already used reset token cannot be reused")
        void resetMentorPassword_ReuseRejected() {
            String rawResetToken = "already_used_reset_token";
            String resetTokenHash = DigestUtils.sha256Hex(rawResetToken);

            when(passwordResetTokenRepository.findByResetTokenHashAndUsedFalse(resetTokenHash))
                    .thenReturn(Optional.empty());

            ResetPasswordRequest req = new ResetPasswordRequest(rawResetToken, "NewMentorPass2026#", "NewMentorPass2026#");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.resetMentorPassword(req));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Faculty OTP Forgot-Password Tests")
    class FacultyOtpTests {

        private User faculty;

        @BeforeEach
        void setUpFaculty() {
            authService.clearRateLimitMap();
            faculty = new User("FAC-102", Role.FACULTY, "Prof. Vikram Verma", "v.verma@intellicampus.edu", "old_hash", dept);
            authService.setJwtSecret("TestSecretKeyForHmacUnitTestingOnly1234567890");
        }

        @Test
        @DisplayName("Success: Generates 6-digit OTP, stores HMAC-SHA256, and dispatches email for Faculty")
        void requestFacultyForgotPassword_Success() {
            ForgotPasswordRequest req = new ForgotPasswordRequest("v.verma@intellicampus.edu", "FACULTY");
            when(userRepository.findByEmailIgnoreCase("v.verma@intellicampus.edu")).thenReturn(Optional.of(faculty));

            ApiResponse resp = authService.requestFacultyForgotPassword(req);

            assertTrue(resp.isSuccess());
            verify(passwordResetTokenRepository, times(1)).invalidateAllActiveTokensForUser(faculty);
            verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
            verify(emailService, times(1)).sendPasswordResetOtp(eq("v.verma@intellicampus.edu"), anyString());
        }

        @Test
        @DisplayName("Success: Unknown faculty email returns generic response without creating OTP")
        void requestFacultyForgotPassword_UnknownEmail() {
            ForgotPasswordRequest req = new ForgotPasswordRequest("unknown@intellicampus.edu", "FACULTY");
            when(userRepository.findByEmailIgnoreCase("unknown@intellicampus.edu")).thenReturn(Optional.empty());

            ApiResponse resp = authService.requestFacultyForgotPassword(req);

            assertTrue(resp.isSuccess());
            verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
            verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString());
        }

        @Test
        @DisplayName("Success: Mentor account requesting faculty OTP returns generic response without creating OTP")
        void requestFacultyForgotPassword_RoleMismatch() {
            User mentor = new User("MEN-201", Role.MENTOR, "Dr. Sunita Mehta", "sunita@intellicampus.edu", "old_hash", dept);
            ForgotPasswordRequest req = new ForgotPasswordRequest("sunita@intellicampus.edu", "FACULTY");
            when(userRepository.findByEmailIgnoreCase("sunita@intellicampus.edu")).thenReturn(Optional.of(mentor));

            ApiResponse resp = authService.requestFacultyForgotPassword(req);

            assertTrue(resp.isSuccess());
            verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
            verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString());
        }

        @Test
        @DisplayName("Success: Verifies correct 6-digit OTP and returns single-use reset token for Faculty")
        void verifyFacultyOtp_Correct() {
            String otp = "654321";
            String otpHmac = authService.computeHmacSha256(otp);
            PasswordResetToken token = new PasswordResetToken(faculty, otpHmac, LocalDateTime.now().plusMinutes(10));

            when(userRepository.findByEmailIgnoreCase("v.verma@intellicampus.edu")).thenReturn(Optional.of(faculty));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(faculty))
                    .thenReturn(Optional.of(token));

            VerifyOtpRequest req = new VerifyOtpRequest("v.verma@intellicampus.edu", otp);
            ApiResponse resp = authService.verifyFacultyOtp(req);

            assertTrue(resp.isSuccess());
            assertNotNull(resp.getData());
            assertNotNull(token.getResetTokenHash());
            assertEquals("VERIFIED_" + token.getId(), token.getTokenHash());
            verify(passwordResetTokenRepository, times(1)).save(token);
        }

        @Test
        @DisplayName("Fails: Wrong OTP for faculty increments attempt counter and returns 400 Bad Request")
        void verifyFacultyOtp_WrongOtp() {
            String otp = "654321";
            String otpHmac = authService.computeHmacSha256(otp);
            PasswordResetToken token = new PasswordResetToken(faculty, otpHmac, LocalDateTime.now().plusMinutes(10));

            when(userRepository.findByEmailIgnoreCase("v.verma@intellicampus.edu")).thenReturn(Optional.of(faculty));
            when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(faculty))
                    .thenReturn(Optional.of(token));

            VerifyOtpRequest req = new VerifyOtpRequest("v.verma@intellicampus.edu", "111111");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.verifyFacultyOtp(req));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Invalid OTP"));
            assertEquals(1, token.getAttempts());
            verify(passwordResetTokenRepository, times(1)).save(token);
        }

        @Test
        @DisplayName("Success: Updates faculty password with BCrypt using verified reset token")
        void resetFacultyPassword_Success() {
            String rawResetToken = "faculty_single_use_reset_token_xyz";
            String resetTokenHash = DigestUtils.sha256Hex(rawResetToken);

            PasswordResetToken token = new PasswordResetToken(faculty, "VERIFIED_2", LocalDateTime.now().plusMinutes(10));
            token.setResetTokenHash(resetTokenHash);

            when(passwordResetTokenRepository.findByResetTokenHashAndUsedFalse(resetTokenHash))
                    .thenReturn(Optional.of(token));
            when(passwordEncoder.encode("NewFacultyPass2026#")).thenReturn("bcrypt_faculty_hash");

            ResetPasswordRequest req = new ResetPasswordRequest(rawResetToken, "NewFacultyPass2026#", "NewFacultyPass2026#");
            ApiResponse resp = authService.resetFacultyPassword(req);

            assertTrue(resp.isSuccess());
            assertEquals("bcrypt_faculty_hash", faculty.getPasswordHash());
            assertTrue(token.getUsed());
            verify(passwordResetTokenRepository, times(1)).save(token);
            verify(passwordResetTokenRepository, times(1)).invalidateAllActiveTokensForUser(faculty);
            verify(userRepository, times(1)).save(faculty);
        }
    }
}
