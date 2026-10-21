package com.intellicampus.auth.service;

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
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.UserRepository;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class StaffAuthService {

    private static final Logger logger = LoggerFactory.getLogger(StaffAuthService.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.dev-mode:false}")
    private boolean devMode = false;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetRateLimitRepository rateLimitRepository;
    private final AuditChainService auditChainService;
    private final EmailService emailService;

    public StaffAuthService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetRateLimitRepository rateLimitRepository,
            AuditChainService auditChainService,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.rateLimitRepository = rateLimitRepository;
        this.auditChainService = auditChainService;
        this.emailService = emailService;
    }

    public PendingStaffApprovalDto registerStaff(StaffRegisterRequestDto req) {
        String email = req.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered: " + email);
        }

        Role role = req.getRole();
        if (role == Role.STUDENT || role == Role.PUBLIC) {
            throw new IllegalArgumentException("Staff registration only supports administrative and academic roles.");
        }

        Department department = null;
        if (req.getDepartment() != null && !req.getDepartment().trim().isEmpty()) {
            String deptName = req.getDepartment().trim();
            department = departmentRepository.findByName(deptName)
                    .orElseGet(() -> departmentRepository.save(new Department(
                            deptName.toUpperCase().replaceAll("[^A-Z0-9]", "_"),
                            deptName
                    )));
        }

        String baseCode = (role == Role.ADMIN ? "ADM-" : role == Role.HOD ? "HOD-" : "FAC-")
                + (req.getEmployeeCode() != null && !req.getEmployeeCode().trim().isEmpty() 
                   ? req.getEmployeeCode().trim().toUpperCase().replaceAll("[^A-Z0-9]", "-")
                   : Math.abs(email.hashCode() % 10000));
        
        String userCode = baseCode;
        int suffix = 1;
        while (userRepository.existsByUserCode(userCode)) {
            userCode = baseCode + "-" + suffix++;
        }

        String passwordHash = passwordEncoder.encode(req.getPassword());

        User user = new User(userCode, role, req.getName().trim(), email, passwordHash, department);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        User savedUser = userRepository.save(user);

        // Audit Event: Registration
        auditChainService.recordEntry(
                savedUser.getId(),
                savedUser.getName(),
                "STAFF_REGISTERED",
                "USER",
                savedUser.getId().toString(),
                "{}",
                "{\"email\":\"" + savedUser.getEmail() + "\",\"role\":\"" + savedUser.getRole() + "\",\"status\":\"ACTIVE\"}",
                "Staff account registered successfully"
        );

        return new PendingStaffApprovalDto(
                savedUser.getId(),
                savedUser.getUserCode(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                department != null ? department.getName() : "General Administration",
                savedUser.getStatus(),
                LocalDateTime.now()
        );
    }

    public AuthResponseDto loginStaff(LoginRequestDto req) {
        String email = req.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check Account Lockout
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                throw new LockedException("Account is temporarily locked due to consecutive failed attempts. Try again later.");
            } else {
                user.setLockedUntil(null);
                user.setFailedAttempts(0);
                if (user.getStatus() == UserStatus.LOCKED) {
                    user.setStatus(UserStatus.ACTIVE);
                }
            }
        }

        // Block accounts according to security rules
        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            throw new DisabledException("Your staff account is currently pending administrator approval. Please contact the administrator.");
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new DisabledException("Your staff registration request has been rejected. Please contact the administrator.");
        }
        if (user.getStatus() == UserStatus.DISABLED || user.getStatus() == UserStatus.INACTIVE || user.getStatus() == UserStatus.SUSPENDED) {
            throw new DisabledException("Account is disabled or suspended. Please contact university administration.");
        }

        // Verify BCrypt password
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            int attempts = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(attempts);

            if (attempts >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                userRepository.save(user);

                // Audit Event: Lockout
                auditChainService.recordEntry(
                        user.getId(),
                        user.getName(),
                        "ACCOUNT_LOCKED",
                        "USER",
                        user.getId().toString(),
                        "{\"failedAttempts\":5}",
                        "{\"status\":\"LOCKED\"}",
                        "Account locked for 15 minutes due to 5 consecutive failed attempts"
                );
            } else {
                userRepository.save(user);
                // Audit Event: Login Failure
                auditChainService.recordEntry(
                        user.getId(),
                        user.getName(),
                        "LOGIN_FAILURE",
                        "USER",
                        user.getId().toString(),
                        "{}",
                        "{\"email\":\"" + user.getEmail() + "\",\"attempts\":" + attempts + "}",
                        "Failed login attempt"
                );
            }

            throw new BadCredentialsException("Invalid email or password");
        }

        // Reset failed login attempts on success
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Issue JWT Access Token
        String accessToken = jwtTokenProvider.generateAccessToken(user, null);

        // Issue and Store Secure Refresh Token
        String rawRefreshToken = generateSecureRandomToken();
        String refreshTokenHash = DigestUtils.sha256Hex(rawRefreshToken);
        refreshTokenRepository.revokeAllForUser(user);
        RefreshToken refreshTokenEntity = new RefreshToken(user, refreshTokenHash, LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(refreshTokenEntity);

        // Audit Event: Login Success
        auditChainService.recordEntry(
                user.getId(),
                user.getName(),
                "LOGIN_SUCCESS",
                "USER",
                user.getId().toString(),
                "{}",
                "{\"email\":\"" + user.getEmail() + "\",\"role\":\"" + user.getRole() + "\"}",
                "Staff authenticated successfully"
        );

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getUserCode(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                null,
                user.getDepartment() != null ? user.getDepartment().getName() : "General",
                null,
                null
        );

        return new AuthResponseDto(accessToken, rawRefreshToken, jwtTokenProvider.getAccessExpirationSeconds(), userSummary);
    }

    public AuthResponseDto refreshAccessToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.trim().isEmpty()) {
            throw new BadCredentialsException("Refresh token is required.");
        }

        String tokenHash = DigestUtils.sha256Hex(rawRefreshToken.trim());
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid or revoked refresh token."));

        if (!tokenEntity.isValid()) {
            throw new BadCredentialsException("Refresh token has expired.");
        }

        User user = tokenEntity.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("Account is not active.");
        }

        // Revoke current refresh token and rotate with new token
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        String newRawRefreshToken = generateSecureRandomToken();
        String newRefreshTokenHash = DigestUtils.sha256Hex(newRawRefreshToken);
        RefreshToken newRefreshTokenEntity = new RefreshToken(user, newRefreshTokenHash, LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(newRefreshTokenEntity);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user, null);

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getUserCode(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                null,
                user.getDepartment() != null ? user.getDepartment().getName() : "General",
                null,
                null
        );

        return new AuthResponseDto(newAccessToken, newRawRefreshToken, jwtTokenProvider.getAccessExpirationSeconds(), userSummary);
    }

    public void logout(String rawRefreshToken, User currentUser) {
        if (rawRefreshToken != null && !rawRefreshToken.trim().isEmpty()) {
            String tokenHash = DigestUtils.sha256Hex(rawRefreshToken.trim());
            refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                    });
        } else if (currentUser != null) {
            refreshTokenRepository.revokeAllForUser(currentUser);
        }

        if (currentUser != null) {
            auditChainService.recordEntry(
                    currentUser.getId(),
                    currentUser.getName(),
                    "LOGOUT",
                    "USER",
                    currentUser.getId().toString(),
                    "{}",
                    "{\"email\":\"" + currentUser.getEmail() + "\"}",
                    "User logged out"
            );
        }
    }

    public String forgotPassword(String emailInput, String clientIp) {
        return forgotPassword(emailInput, null, clientIp);
    }

    public String forgotPassword(String emailInput, Role role, String clientIp) {
        String email = emailInput != null ? emailInput.trim().toLowerCase() : "";
        String ip = clientIp != null ? clientIp.trim() : "UNKNOWN";

        // Rate Limiting: Max 3 requests per hour per email and per IP
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long emailCount = rateLimitRepository.countByEmailAndRequestedAtAfter(email, oneHourAgo);
        long ipCount = rateLimitRepository.countByIpAddressAndRequestedAtAfter(ip, oneHourAgo);

        if (emailCount >= 3 || ipCount >= 3) {
            LocalDateTime oldest = null;
            if (emailCount >= 3) {
                List<PasswordResetRateLimit> list = rateLimitRepository.findByEmailAndRequestedAtAfterOrderByRequestedAtAsc(email, oneHourAgo);
                if (list != null && !list.isEmpty()) oldest = list.get(0).getRequestedAt();
            }
            if (ipCount >= 3) {
                List<PasswordResetRateLimit> list = rateLimitRepository.findByIpAddressAndRequestedAtAfterOrderByRequestedAtAsc(ip, oneHourAgo);
                if (list != null && !list.isEmpty()) {
                    LocalDateTime ipOldest = list.get(0).getRequestedAt();
                    if (oldest == null || (ipOldest != null && ipOldest.isBefore(oldest))) {
                        oldest = ipOldest;
                    }
                }
            }
            long minutesRemaining = 60;
            if (oldest != null) {
                long diff = java.time.Duration.between(LocalDateTime.now(), oldest.plusHours(1)).toMinutes() + 1;
                minutesRemaining = Math.max(1, Math.min(60, diff));
            }
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many password reset requests. Maximum 3 requests per hour are allowed. Please try again in " + minutesRemaining + " minute" + (minutesRemaining == 1 ? "" : "s") + ".");
        }

        String genericSuccessMessage = "If the account exists, a 6-digit password reset OTP has been sent to your registered email.";

        // Unknown emails receive the exact same generic response
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            logger.info("Password reset request: user not found for email {}", email);
            if (devMode) {
                logger.debug("[DEV-MODE] Forgot password email skipped for email {}. Reason: user not found in database", email);
            }
            rateLimitRepository.save(new PasswordResetRateLimit(email, ip));
            return genericSuccessMessage;
        }

        User user = userOpt.get();
        // Look up the user by email AND role. Only send OTP if account exists and role matches.
        boolean roleMatches = (role == null) || (user.getRole() == role);
        if (!roleMatches) {
            logger.info("Password reset request: role mismatch (registered as {}, requested {}) for email {}", user.getRole(), role, email);
            if (devMode) {
                logger.debug("[DEV-MODE] Forgot password email skipped for email {}. Reason: role mismatch (registered as {}, requested {})", email, user.getRole(), role);
            }
            rateLimitRepository.save(new PasswordResetRateLimit(email, ip));
            return genericSuccessMessage;
        }

        if (user.getStatus() == UserStatus.DISABLED || user.getStatus() == UserStatus.REJECTED) {
            logger.info("Password reset request: account inactive (status: {}) for email {}", user.getStatus(), email);
            if (devMode) {
                logger.debug("[DEV-MODE] Forgot password email skipped for email {}. Reason: user status is {}", email, user.getStatus());
            }
            rateLimitRepository.save(new PasswordResetRateLimit(email, ip));
            return genericSuccessMessage;
        }

        // Generate cryptographically secure 6-digit OTP (000000 - 999999)
        SecureRandom random = new SecureRandom();
        int otpNum = random.nextInt(1_000_000);
        String rawOtp = String.format("%06d", otpNum);
        logger.debug("[DEV] Generated OTP for {}: {}", user.getEmail(), rawOtp);

        // Store only the SHA-256 hash of the OTP, never plain text
        String tokenHash = DigestUtils.sha256Hex(rawOtp);

        // Invalidate existing unused reset tokens/OTPs for this user
        passwordResetTokenRepository.invalidateAllForUser(user);

        // Expiry: 10 minutes, single use, 0 attempts
        PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, LocalDateTime.now().plusMinutes(10));
        passwordResetTokenRepository.save(resetToken);

        // Audit Event: Password Reset Requested (never log OTPs)
        auditChainService.recordEntry(
                user.getId(),
                user.getName(),
                "PASSWORD_RESET_OTP_REQUESTED",
                "USER",
                user.getId().toString(),
                "{}",
                "{\"email\":\"" + user.getEmail() + "\",\"role\":\"" + user.getRole() + "\"}",
                "Password reset OTP requested"
        );

        logger.info("Password reset request: OTP generated and email send attempted for email {}", user.getEmail());

        // Send OTP to registered email. If send fails, rate limit quota is NOT consumed.
        try {
            emailService.sendPasswordResetOtp(user.getEmail(), rawOtp);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            logger.error("SMTP delivery failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to deliver OTP email via SMTP: " + e.getMessage());
        }

        // Consume rate limit quota only on successful send
        rateLimitRepository.save(new PasswordResetRateLimit(email, ip));

        return genericSuccessMessage;
    }

    public void resetPassword(String rawToken, String newPassword) {
        ResetPasswordRequestDto req = new ResetPasswordRequestDto(rawToken, newPassword);
        resetPasswordWithOtp(req);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void resetPasswordWithOtp(ResetPasswordRequestDto req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset password request cannot be empty.");
        }

        String rawOtpOrToken = req.getOtpOrToken();
        if (rawOtpOrToken == null || rawOtpOrToken.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "6-digit OTP is required.");
        }
        String enteredOtp = rawOtpOrToken.trim();

        String newPassword = req.getNewPassword();
        validateStrongPassword(newPassword);

        if (req.getConfirmPassword() != null && !req.getConfirmPassword().trim().isEmpty()) {
            if (!req.getConfirmPassword().trim().equals(newPassword)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
            }
        }

        PasswordResetToken resetToken;
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : null;

        if (email != null && !email.isEmpty()) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP."));
            resetToken = passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP."));
        } else {
            String tokenHash = DigestUtils.sha256Hex(enteredOtp);
            resetToken = passwordResetTokenRepository.findByTokenHashAndUsedFalse(tokenHash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP."));
        }

        // Check if attempts exceeded max (5)
        if (resetToken.getAttempts() >= 5) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. This OTP has been invalidated. Please request a new OTP.");
        }

        // Check expiration (10-minute validity)
        if (resetToken.isExpired()) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OTP has expired (10-minute validity). Please request a new OTP.");
        }

        // Verify OTP hash
        String enteredHash = DigestUtils.sha256Hex(enteredOtp);
        if (!resetToken.getTokenHash().equals(enteredHash)) {
            resetToken.incrementAttempts();
            if (resetToken.getAttempts() >= 5) {
                resetToken.setUsed(true);
                passwordResetTokenRepository.save(resetToken);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many incorrect attempts. This OTP has been invalidated. Please request a new OTP.");
            }
            passwordResetTokenRepository.save(resetToken);
            int remaining = 5 - resetToken.getAttempts();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // Mark OTP as used (cannot be reused)
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Update password hash, reset lockout/failed attempts
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        // Revoke active refresh tokens
        refreshTokenRepository.revokeAllForUser(user);

        // Audit Event: Password Reset Completed
        auditChainService.recordEntry(
                user.getId(),
                user.getName(),
                "PASSWORD_RESET_COMPLETED",
                "USER",
                user.getId().toString(),
                "{}",
                "{\"email\":\"" + user.getEmail() + "\"}",
                "Password successfully reset with OTP"
        );
    }

    private void validateStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters long.");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{}|;':\",./<>?`~".indexOf(ch) >= 0);

        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must include uppercase, lowercase, number, and special character.");
        }
    }

    public void changePassword(User currentUser, ChangePasswordRequestDto req) {
        if (!passwordEncoder.matches(req.getCurrentPassword(), currentUser.getPasswordHash())) {
            throw new BadCredentialsException("Current password does not match.");
        }

        currentUser.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        currentUser.setMustChangePassword(false);
        userRepository.save(currentUser);

        // Revoke active refresh tokens on password change
        refreshTokenRepository.revokeAllForUser(currentUser);

        // Audit Event: Password Changed
        auditChainService.recordEntry(
                currentUser.getId(),
                currentUser.getName(),
                "PASSWORD_CHANGED",
                "USER",
                currentUser.getId().toString(),
                "{}",
                "{\"email\":\"" + currentUser.getEmail() + "\"}",
                "User changed password"
        );
    }

    public UserSummaryDto getCurrentStaff(User currentUser) {
        return new UserSummaryDto(
                currentUser.getId(),
                currentUser.getUserCode(),
                currentUser.getName(),
                currentUser.getEmail(),
                currentUser.getRole(),
                null,
                currentUser.getDepartment() != null ? currentUser.getDepartment().getName() : "General",
                null,
                null
        );
    }

    // Admin Approval Workflows
    public List<PendingStaffApprovalDto> getPendingApprovals() {
        return userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.PENDING_APPROVAL)
                .map(u -> new PendingStaffApprovalDto(
                        u.getId(),
                        u.getUserCode(),
                        u.getName(),
                        u.getEmail(),
                        u.getRole(),
                        u.getDepartment() != null ? u.getDepartment().getName() : "General Administration",
                        u.getStatus(),
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    public void approveStaff(Long staffUserId, User adminUser) {
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found: " + staffUserId));

        if (staff.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("User is not awaiting approval (current status: " + staff.getStatus() + ")");
        }

        staff.setStatus(UserStatus.ACTIVE);
        userRepository.save(staff);

        // Audit Event: Approval
        auditChainService.recordEntry(
                adminUser.getId(),
                adminUser.getName(),
                "STAFF_APPROVED",
                "USER",
                staff.getId().toString(),
                "{\"status\":\"PENDING_APPROVAL\"}",
                "{\"status\":\"ACTIVE\"}",
                "Staff approved by Admin " + adminUser.getName()
        );
    }

    public void rejectStaff(Long staffUserId, String reason, User adminUser) {
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found: " + staffUserId));

        staff.setStatus(UserStatus.REJECTED);
        userRepository.save(staff);

        // Audit Event: Rejection
        auditChainService.recordEntry(
                adminUser.getId(),
                adminUser.getName(),
                "STAFF_REJECTED",
                "USER",
                staff.getId().toString(),
                "{\"status\":\"PENDING_APPROVAL\"}",
                "{\"status\":\"REJECTED\"}",
                reason != null && !reason.trim().isEmpty() ? reason.trim() : "Rejected by Admin " + adminUser.getName()
        );
    }

    private String generateSecureRandomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Hex.encodeHexString(bytes);
    }
}
