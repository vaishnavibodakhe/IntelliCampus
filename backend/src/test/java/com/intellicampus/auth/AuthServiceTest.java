package com.intellicampus.auth;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.auth.dto.*;
import com.intellicampus.auth.model.PasswordResetToken;
import com.intellicampus.auth.repository.PasswordResetTokenRepository;
import com.intellicampus.auth.service.AuthService;
import com.intellicampus.auth.service.JwtTokenProvider;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.FaceEnrollmentService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DivisionRepository divisionRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private FaceEnrollmentService faceEnrollmentService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    private User studentUser;
    private StudentProfile studentProfile;
    private Department csDept;
    private Division divA;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                studentProfileRepository,
                departmentRepository,
                divisionRepository,
                passwordResetTokenRepository,
                faceEnrollmentService,
                passwordEncoder,
                jwtTokenProvider,
                true
        );

        csDept = new Department("CS", "Computer Science");
        csDept.setId(1L);

        divA = new Division(csDept, 3, "Year 3 Div A");
        divA.setId(10L);

        studentUser = new User();
        studentUser.setId(5L);
        studentUser.setUserCode("student.aarav");
        studentUser.setName("Aarav Shah");
        studentUser.setEmail("aarav.cs24@intellicampus.edu");
        studentUser.setPasswordHash("$2a$10$hashedPasswordPlaceholder");
        studentUser.setRole(Role.STUDENT);
        studentUser.setDepartment(csDept);
        studentUser.setStatus(UserStatus.ACTIVE);
        studentUser.setFailedAttempts(0);

        studentProfile = new StudentProfile(studentUser, "CS-104", divA, null);
        studentProfile.setId(100L);
        studentProfile.setFaceEnrolled(true);
        studentProfile.setAttendanceRate(85.0);
    }

    @Test
    @DisplayName("Login - Successful with valid credentials")
    void testLogin_Success() {
        LoginRequestDto req = new LoginRequestDto("student.aarav", "ValidPass123!");

        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));
        when(passwordEncoder.matches("ValidPass123!", studentUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateToken(studentUser)).thenReturn("mock.jwt.token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(900L);
        when(studentProfileRepository.findByUser(studentUser)).thenReturn(Optional.of(studentProfile));

        AuthResponseDto resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("mock.jwt.token", resp.getAccessToken());
        assertEquals("Bearer", resp.getTokenType());
        assertEquals(900L, resp.getExpiresIn());
        assertNotNull(resp.getUser());
        assertEquals("student.aarav", resp.getUser().getUserCode());
        assertEquals(Role.STUDENT, resp.getUser().getRole());
        assertEquals("CS-104", resp.getUser().getRollNo());
    }

    @Test
    @DisplayName("Login - Fails with wrong password and increments failed attempts")
    void testLogin_WrongPassword() {
        LoginRequestDto req = new LoginRequestDto("student.aarav", "WrongPassword");

        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));
        when(passwordEncoder.matches("WrongPassword", studentUser.getPasswordHash())).thenReturn(false);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.login(req));
        assertEquals("Invalid login code or password.", ex.getMessage());
        assertEquals(1, studentUser.getFailedAttempts());
        verify(userRepository).save(studentUser);
    }

    @Test
    @DisplayName("Login - Fails with unknown user and returns identical generic error")
    void testLogin_UnknownUser() {
        LoginRequestDto req = new LoginRequestDto("unknown.user", "AnyPassword");

        when(userRepository.findByUserCode("unknown.user")).thenReturn(Optional.empty());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.login(req));
        assertEquals("Invalid login code or password.", ex.getMessage());
    }

    @Test
    @DisplayName("Login - Enforces 5-minute lockout after 5 consecutive failures")
    void testLogin_LockoutAfter5Failures() {
        studentUser.setFailedAttempts(4);
        LoginRequestDto req = new LoginRequestDto("student.aarav", "WrongPassword");

        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));
        when(passwordEncoder.matches("WrongPassword", studentUser.getPasswordHash())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(req));
        assertEquals(5, studentUser.getFailedAttempts());
        assertNotNull(studentUser.getLockedUntil());
        assertTrue(studentUser.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(4)));
    }

    @Test
    @DisplayName("Login - Rejects immediately when account is locked")
    void testLogin_AccountCurrentlyLocked() {
        studentUser.setLockedUntil(LocalDateTime.now().plusMinutes(3));
        LoginRequestDto req = new LoginRequestDto("student.aarav", "AnyPass");

        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));

        AuthService.AccountLockedException ex = assertThrows(
                AuthService.AccountLockedException.class,
                () -> authService.login(req)
        );
        assertTrue(ex.getMessage().contains("Account temporarily locked"));
    }

    @Test
    @DisplayName("Register - Success creates student and calls FaceEnrollmentService")
    void testRegister_Success() {
        RegisterRequestDto req = new RegisterRequestDto();
        req.setName("Neha Sharma");
        req.setEmail("neha@intellicampus.edu");
        req.setLoginCode("student.neha");
        req.setRollNo("CS-109");
        req.setDepartment("Computer Science");
        req.setYear("Year 3");
        req.setDivision("Div A");
        req.setPassword("SecurePass2026!");
        req.setConfirmPassword("SecurePass2026!");
        req.setBiometricConsent(true);
        req.setConsentVersion("1.0");

        MockMultipartFile photo = new MockMultipartFile(
                "photo", "student.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}
        );

        when(userRepository.existsByUserCode("student.neha")).thenReturn(false);
        when(userRepository.existsByEmail("neha@intellicampus.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRollNo("CS-109")).thenReturn(false);
        when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));
        when(divisionRepository.findByNameAndDepartmentId("Div A", 1L)).thenReturn(Optional.of(divA));
        when(passwordEncoder.encode("SecurePass2026!")).thenReturn("$2a$10$encodedPassword");

        User savedUser = new User("student.neha", Role.STUDENT, "Neha Sharma", "neha@intellicampus.edu", "$2a$10$encodedPassword", csDept);
        savedUser.setId(12L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        StudentProfile savedProf = new StudentProfile(savedUser, "CS-109", divA, null);
        savedProf.setId(200L);
        when(studentProfileRepository.save(any(StudentProfile.class))).thenReturn(savedProf);

        StudentProfile enrolledProf = new StudentProfile(savedUser, "CS-109", divA, null);
        enrolledProf.setId(200L);
        enrolledProf.setFaceEnrolled(true);
        when(faceEnrollmentService.enrollStudentFace(eq(200L), eq(photo), eq("1.0"))).thenReturn(enrolledProf);

        UserSummaryDto result = authService.register(req, photo);

        assertNotNull(result);
        assertEquals("student.neha", result.getUserCode());
        assertEquals("Neha Sharma", result.getName());
        assertEquals(Role.STUDENT, result.getRole());
        assertEquals("CS-109", result.getRollNo());
        assertTrue(result.getFaceEnrolled());

        verify(faceEnrollmentService).enrollStudentFace(eq(200L), eq(photo), eq("1.0"));
    }

    @Test
    @DisplayName("Register - Rejects duplicate login code")
    void testRegister_DuplicateLoginCode() {
        RegisterRequestDto req = new RegisterRequestDto();
        req.setName("Neha Sharma");
        req.setEmail("neha@intellicampus.edu");
        req.setLoginCode("student.aarav");
        req.setRollNo("CS-109");
        req.setDepartment("Computer Science");
        req.setDivision("Div A");
        req.setPassword("SecurePass2026!");
        req.setBiometricConsent(true);

        MockMultipartFile photo = new MockMultipartFile("photo", "photo.jpg", "image/jpeg", new byte[]{1, 2});
        when(userRepository.existsByUserCode("student.aarav")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req, photo));
        assertTrue(ex.getMessage().contains("Login code already in use"));
    }

    @Test
    @DisplayName("Register - Rejects weak password missing number")
    void testRegister_WeakPassword() {
        RegisterRequestDto req = new RegisterRequestDto();
        req.setName("Neha Sharma");
        req.setEmail("neha@intellicampus.edu");
        req.setLoginCode("student.neha");
        req.setRollNo("CS-109");
        req.setDepartment("Computer Science");
        req.setDivision("Div A");
        req.setPassword("onlyletters");
        req.setBiometricConsent(true);

        MockMultipartFile photo = new MockMultipartFile("photo", "photo.jpg", "image/jpeg", new byte[]{1, 2});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req, photo));
        assertTrue(ex.getMessage().contains("Password must be at least 8 characters"));
    }

    @Test
    @DisplayName("Register - Rejects missing biometric consent")
    void testRegister_MissingConsent() {
        RegisterRequestDto req = new RegisterRequestDto();
        req.setName("Neha Sharma");
        req.setEmail("neha@intellicampus.edu");
        req.setLoginCode("student.neha");
        req.setRollNo("CS-109");
        req.setDepartment("Computer Science");
        req.setDivision("Div A");
        req.setPassword("SecurePass2026!");
        req.setBiometricConsent(false);

        MockMultipartFile photo = new MockMultipartFile("photo", "photo.jpg", "image/jpeg", new byte[]{1, 2});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req, photo));
        assertTrue(ex.getMessage().contains("Biometric consent is mandatory"));
    }

    @Test
    @DisplayName("Register - Face enrollment failure throws exception and aborts registration")
    void testRegister_FaceEnrollmentFailure() {
        RegisterRequestDto req = new RegisterRequestDto();
        req.setName("Neha Sharma");
        req.setEmail("neha@intellicampus.edu");
        req.setLoginCode("student.neha");
        req.setRollNo("CS-109");
        req.setDepartment("Computer Science");
        req.setDivision("Div A");
        req.setPassword("SecurePass2026!");
        req.setBiometricConsent(true);

        MockMultipartFile photo = new MockMultipartFile("photo", "photo.jpg", "image/jpeg", new byte[]{1, 2});

        when(userRepository.existsByUserCode("student.neha")).thenReturn(false);
        when(userRepository.existsByEmail("neha@intellicampus.edu")).thenReturn(false);
        when(studentProfileRepository.existsByRollNo("CS-109")).thenReturn(false);
        when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));
        when(divisionRepository.findByNameAndDepartmentId("Div A", 1L)).thenReturn(Optional.of(divA));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");

        User savedUser = new User();
        savedUser.setId(12L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        StudentProfile savedProf = new StudentProfile();
        savedProf.setId(200L);
        when(studentProfileRepository.save(any(StudentProfile.class))).thenReturn(savedProf);

        when(faceEnrollmentService.enrollStudentFace(eq(200L), eq(photo), any()))
                .thenThrow(new IllegalArgumentException("Expected exactly 1 face, but detected 0"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req, photo));
        assertTrue(ex.getMessage().contains("Expected exactly 1 face, but detected 0"));
    }

    // =========================================================================
    // Forgot Password & Reset Password Tests
    // =========================================================================

    @Test
    @DisplayName("Forgot Password - Returns neutral message and saves 6-digit hashed code when user exists")
    void testForgotPassword_UserExists() {
        ForgotPasswordRequestDto req = new ForgotPasswordRequestDto("student.aarav");
        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));
        when(passwordResetTokenRepository.findByUserAndUsedFalse(studentUser)).thenReturn(Collections.emptyList());

        ForgotPasswordResponseDto resp = authService.forgotPassword(req);

        assertNotNull(resp);
        assertEquals("If the account exists, a reset code has been generated.", resp.getMessage());
        assertNotNull(resp.getDemoResetCode());
        assertEquals(6, resp.getDemoResetCode().length());

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Forgot Password - Returns identical neutral message when user does NOT exist")
    void testForgotPassword_UserNotFound() {
        ForgotPasswordRequestDto req = new ForgotPasswordRequestDto("unknown@intellicampus.edu");
        when(userRepository.findByUserCode("unknown@intellicampus.edu")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown@intellicampus.edu")).thenReturn(Optional.empty());

        ForgotPasswordResponseDto resp = authService.forgotPassword(req);

        assertNotNull(resp);
        assertEquals("If the account exists, a reset code has been generated.", resp.getMessage());
        assertNull(resp.getDemoResetCode());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset Password - Valid code successfully updates password with BCrypt")
    void testResetPassword_ValidCode_Success() {
        String rawCode = "123456";
        String codeHash = DigestUtils.sha256Hex(rawCode);

        PasswordResetToken token = new PasswordResetToken(studentUser, codeHash, LocalDateTime.now().plusMinutes(10));
        token.setId(1L);

        ResetPasswordRequestDto req = new ResetPasswordRequestDto(rawCode, "NewPassword2026!", "NewPassword2026!");
        req.setLoginCode("student.aarav");

        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));
        when(passwordResetTokenRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(studentUser))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword2026!")).thenReturn("$2a$10$newBcryptHashedPassword");
        when(passwordResetTokenRepository.findByUser(studentUser)).thenReturn(List.of(token));

        ResetPasswordResponseDto resp = authService.resetPassword(req);

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertTrue(resp.getMessage().contains("Password reset successfully"));

        verify(userRepository).save(studentUser);
        assertEquals("$2a$10$newBcryptHashedPassword", studentUser.getPasswordHash());
        verify(passwordResetTokenRepository).saveAll(any());
    }

    @Test
    @DisplayName("Reset Password - Wrong code increments attempt counter and rejects")
    void testResetPassword_WrongCode_IncrementsAttempts() {
        String correctHash = DigestUtils.sha256Hex("654321");
        PasswordResetToken token = new PasswordResetToken(studentUser, correctHash, LocalDateTime.now().plusMinutes(10));
        token.setId(1L);
        token.setAttempts(1);

        ResetPasswordRequestDto req = new ResetPasswordRequestDto("111111", "NewPassword2026!", "NewPassword2026!");
        req.setLoginCode("student.aarav");

        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));
        when(passwordResetTokenRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(studentUser))
                .thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(req));
        assertTrue(ex.getMessage().contains("Invalid reset code. Attempt 2 of 5."));
        assertEquals(2, token.getAttempts());
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    @DisplayName("Reset Password - Maximum 5 wrong attempts invalidates token")
    void testResetPassword_Exceeds5Attempts() {
        String correctHash = DigestUtils.sha256Hex("654321");
        PasswordResetToken token = new PasswordResetToken(studentUser, correctHash, LocalDateTime.now().plusMinutes(10));
        token.setAttempts(5);

        ResetPasswordRequestDto req = new ResetPasswordRequestDto("111111", "NewPassword2026!", "NewPassword2026!");
        req.setLoginCode("student.aarav");

        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));
        when(passwordResetTokenRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(studentUser))
                .thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(req));
        assertTrue(ex.getMessage().contains("Maximum reset attempts exceeded"));
        assertTrue(token.getUsed());
    }

    @Test
    @DisplayName("Reset Password - Expired code is rejected")
    void testResetPassword_ExpiredCode() {
        String code = "123456";
        String codeHash = DigestUtils.sha256Hex(code);

        PasswordResetToken token = new PasswordResetToken(studentUser, codeHash, LocalDateTime.now().minusMinutes(5));

        ResetPasswordRequestDto req = new ResetPasswordRequestDto(code, "NewPassword2026!", "NewPassword2026!");
        req.setLoginCode("student.aarav");

        when(userRepository.findByUserCode("student.aarav")).thenReturn(Optional.of(studentUser));
        when(passwordResetTokenRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(studentUser))
                .thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(req));
        assertTrue(ex.getMessage().contains("Reset code has expired"));
    }

    @Test
    @DisplayName("Reset Password - Reused code is rejected")
    void testResetPassword_ReusedCode() {
        String code = "123456";
        String codeHash = DigestUtils.sha256Hex(code);

        PasswordResetToken token = new PasswordResetToken(studentUser, codeHash, LocalDateTime.now().plusMinutes(5));
        token.setUsed(true);

        ResetPasswordRequestDto req = new ResetPasswordRequestDto(code, "NewPassword2026!", "NewPassword2026!");

        when(passwordResetTokenRepository.findByTokenHashAndUsedFalse(codeHash)).thenReturn(Optional.empty());
        when(passwordResetTokenRepository.findByTokenHash(codeHash)).thenReturn(Optional.of(token));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.resetPassword(req));
        assertTrue(ex.getMessage().contains("Reset code has already been used"));
    }
}
