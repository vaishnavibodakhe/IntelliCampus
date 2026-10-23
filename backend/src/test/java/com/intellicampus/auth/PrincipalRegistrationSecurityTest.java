package com.intellicampus.auth;

import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.auth.dto.AdminOtpVerifyDto;
import com.intellicampus.auth.dto.LoginRequestDto;
import com.intellicampus.auth.dto.PrincipalRegisterRequestDto;
import com.intellicampus.auth.dto.PrincipalSetupStatusDto;
import com.intellicampus.auth.service.AuthRateLimiter;
import com.intellicampus.auth.service.EmailService;
import com.intellicampus.auth.service.JwtTokenProvider;
import com.intellicampus.auth.service.StaffAuthService;
import com.intellicampus.user.model.PasswordResetToken;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.auth.repository.RefreshTokenRepository;
import com.intellicampus.user.repository.PasswordResetTokenRepository;
import com.intellicampus.user.repository.UserRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrincipalRegistrationSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditChainService auditChainService;

    @Mock
    private EmailService emailService;

    private AuthRateLimiter authRateLimiter;
    private StaffAuthService staffAuthService;

    @BeforeEach
    void setUp() {
        authRateLimiter = new AuthRateLimiter();
        staffAuthService = new StaffAuthService(
                userRepository,
                null,
                passwordEncoder,
                jwtTokenProvider,
                passwordResetTokenRepository,
                refreshTokenRepository,
                null,
                auditChainService,
                emailService,
                authRateLimiter
        );
    }

    @Test
    @DisplayName("Setup status: returns setupRequired=true when no ACTIVE Principal exists")
    void testSetupStatusWhenNoPrincipal() {
        when(userRepository.existsByRoleAndStatus(Role.PRINCIPAL, UserStatus.ACTIVE)).thenReturn(false);

        PrincipalSetupStatusDto status = staffAuthService.getPrincipalSetupStatus();
        assertTrue(status.isSetupRequired());
    }

    @Test
    @DisplayName("Setup status: returns setupRequired=false when ACTIVE Principal exists")
    void testSetupStatusWhenPrincipalExists() {
        when(userRepository.existsByRoleAndStatus(Role.PRINCIPAL, UserStatus.ACTIVE)).thenReturn(true);

        PrincipalSetupStatusDto status = staffAuthService.getPrincipalSetupStatus();
        assertFalse(status.isSetupRequired());
    }

    @Test
    @DisplayName("Register succeeds when no Principal exists (status=PENDING_APPROVAL, OTP dispatched)")
    void testRegisterSucceedsWhenNoPrincipal() {
        when(userRepository.findFirstByRole(Role.PRINCIPAL)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("principal@college.edu")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        PrincipalRegisterRequestDto req = new PrincipalRegisterRequestDto(
                "Dr. A. Sharma",
                "principal@college.edu",
                "SecurePass@2026",
                "SecurePass@2026",
                null
        );

        Map<String, Object> res = staffAuthService.registerPrincipal(req, "127.0.0.1");

        assertNotNull(res);
        assertEquals(true, res.get("pendingVerification"));
        assertEquals("principal@college.edu", res.get("email"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User created = userCaptor.getValue();
        assertEquals(Role.PRINCIPAL, created.getRole());
        assertEquals(UserStatus.PENDING_APPROVAL, created.getStatus());

        verify(emailService).sendPrincipalRegistrationOtp(eq("principal@college.edu"), anyString());
    }

    @Test
    @DisplayName("Second register returns 403 Forbidden once an ACTIVE Principal exists")
    void testSecondRegisterReturns403() {
        User activePrincipal = new User("PRIN-001", Role.PRINCIPAL, "Active Principal", "dr.first@college.edu", "hash", null);
        activePrincipal.setStatus(UserStatus.ACTIVE);

        when(userRepository.findFirstByRole(Role.PRINCIPAL)).thenReturn(Optional.of(activePrincipal));

        PrincipalRegisterRequestDto req = new PrincipalRegisterRequestDto(
                "Second Principal",
                "dr.second@college.edu",
                "SecurePass@2026",
                "SecurePass@2026",
                null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> staffAuthService.registerPrincipal(req, "127.0.0.1"));

        assertEquals(403, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("already exists"));
    }

    @Test
    @DisplayName("Concurrent registration creates only one Principal: DB unique constraint throws 403")
    void testConcurrentRegistrationFailsWith403OnDbConflict() {
        when(userRepository.findFirstByRole(Role.PRINCIPAL)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("concurrent@college.edu")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '1' for key 'uq_users_principal_singleton'"));

        PrincipalRegisterRequestDto req = new PrincipalRegisterRequestDto(
                "Concurrent Principal",
                "concurrent@college.edu",
                "SecurePass@2026",
                "SecurePass@2026",
                null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> staffAuthService.registerPrincipal(req, "127.0.0.1"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Unverified pending account cannot log in: throws DisabledException")
    void testUnverifiedAccountCannotLogin() {
        User pendingPrincipal = new User("PRIN-001", Role.PRINCIPAL, "Pending Principal", "pending@college.edu", "hash", null);
        pendingPrincipal.setStatus(UserStatus.PENDING_APPROVAL);

        when(userRepository.findByEmail("pending@college.edu")).thenReturn(Optional.of(pendingPrincipal));

        LoginRequestDto req = new LoginRequestDto("pending@college.edu", "AnyPass@2026");

        DisabledException ex = assertThrows(DisabledException.class, () -> staffAuthService.loginPrincipal(req));
        assertTrue(ex.getMessage().contains("pending email verification"));
    }

    @Test
    @DisplayName("Weak password rejected: requires minimum 8 characters, upper, lower, number, special char")
    void testWeakPasswordRejected() {
        when(userRepository.findFirstByRole(Role.PRINCIPAL)).thenReturn(Optional.empty());

        // Less than 8 characters
        PrincipalRegisterRequestDto shortPassReq = new PrincipalRegisterRequestDto(
                "Principal", "prin@college.edu", "Ab1!", "Ab1!", null
        );
        ResponseStatusException exShort = assertThrows(ResponseStatusException.class,
                () -> staffAuthService.registerPrincipal(shortPassReq, "127.0.0.1"));
        assertEquals(400, exShort.getStatusCode().value());

        // Missing special character
        PrincipalRegisterRequestDto noSpecialReq = new PrincipalRegisterRequestDto(
                "Principal", "prin@college.edu", "Password123", "Password123", null
        );
        ResponseStatusException exNoSpecial = assertThrows(ResponseStatusException.class,
                () -> staffAuthService.registerPrincipal(noSpecialReq, "127.0.0.1"));
        assertEquals(400, exNoSpecial.getStatusCode().value());
    }

    @Test
    @DisplayName("Wrong OTP is rejected with BadCredentialsException")
    void testWrongOtpRejected() {
        User pendingPrincipal = new User("PRIN-001", Role.PRINCIPAL, "Principal", "prin@college.edu", "hash", null);
        pendingPrincipal.setStatus(UserStatus.PENDING_APPROVAL);

        PasswordResetToken token = new PasswordResetToken(pendingPrincipal, DigestUtils.sha256Hex("123456"), LocalDateTime.now().plusMinutes(15));

        when(userRepository.findByEmail("prin@college.edu")).thenReturn(Optional.of(pendingPrincipal));
        when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(pendingPrincipal))
                .thenReturn(Optional.of(token));

        AdminOtpVerifyDto req = new AdminOtpVerifyDto("prin@college.edu", "999999");

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> staffAuthService.verifyPrincipalOtp(req, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("Invalid OTP"));
    }

    @Test
    @DisplayName("Expired OTP is rejected with BadCredentialsException")
    void testExpiredOtpRejected() {
        User pendingPrincipal = new User("PRIN-001", Role.PRINCIPAL, "Principal", "prin@college.edu", "hash", null);
        pendingPrincipal.setStatus(UserStatus.PENDING_APPROVAL);

        // Expired token
        PasswordResetToken token = new PasswordResetToken(pendingPrincipal, DigestUtils.sha256Hex("123456"), LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByEmail("prin@college.edu")).thenReturn(Optional.of(pendingPrincipal));
        when(passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(pendingPrincipal))
                .thenReturn(Optional.of(token));

        AdminOtpVerifyDto req = new AdminOtpVerifyDto("prin@college.edu", "123456");

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> staffAuthService.verifyPrincipalOtp(req, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("SETUP_TOKEN enforced when configured in environment")
    void testSetupTokenEnforcedWhenSet() {
        staffAuthService.setSetupToken("SECRET_CAMPUS_TOKEN_2026");

        // Request with missing or invalid token
        PrincipalRegisterRequestDto req = new PrincipalRegisterRequestDto(
                "Principal", "prin@college.edu", "SecurePass@2026", "SecurePass@2026", "WRONG_TOKEN"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> staffAuthService.registerPrincipal(req, "127.0.0.1"));
        assertEquals(403, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Invalid setup token"));

        // Request with valid token proceeds to next check
        when(userRepository.findFirstByRole(Role.PRINCIPAL)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("prin@college.edu")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        PrincipalRegisterRequestDto validReq = new PrincipalRegisterRequestDto(
                "Principal", "prin@college.edu", "SecurePass@2026", "SecurePass@2026", "SECRET_CAMPUS_TOKEN_2026"
        );
        Map<String, Object> res = staffAuthService.registerPrincipal(validReq, "127.0.0.1");
        assertNotNull(res);
        assertEquals(true, res.get("pendingVerification"));
    }
}
