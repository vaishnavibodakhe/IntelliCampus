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
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private JwtTokenProvider tokenProvider = new JwtTokenProvider("4c8a91f2b6e3d7a8c1f4e5a9b2d8c3e7f1a5b9c2d8e4f1a7b3c9d5e2f8a4b6c1", 15);
    private EmailService emailService = new EmailService(null);

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
}
