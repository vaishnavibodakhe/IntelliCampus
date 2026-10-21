package com.intellicampus.user.service;

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
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.PasswordResetTokenRepository;
import com.intellicampus.user.repository.RevokedTokenRepository;
import com.intellicampus.user.repository.UserRepository;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service("facultyMentorAuthService")
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Password complexity pattern: min 8 chars, 1 uppercase, 1 lowercase, 1 digit
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern MENTOR_ID_PATTERN = Pattern.compile("^MEN-\\d{3,}$");

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;

    @Value("${app.jwt.secret:${JWT_SECRET:IntelliCampusDefaultSecretKeyForHmacSigning1234567890}}")
    private String jwtSecret = "IntelliCampusDefaultSecretKeyForHmacSigning1234567890";

    // In-memory rate limiting tracker for forgot-password: email -> timestamp of last request
    private final Map<String, LocalDateTime> forgotPasswordRateLimitMap = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       DepartmentRepository departmentRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       RevokedTokenRepository revokedTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.emailService = emailService;
    }

    /**
     * Register a new Faculty member
     */
    public AuthResponse registerFaculty(FacultyRegisterRequest req) {
        validateRegistrationFields(req.getFullName(), req.getFacultyId(), req.getEmail(),
                req.getPhone(), req.getPassword(), req.getConfirmPassword());

        String email = req.getEmail().trim().toLowerCase();
        String facultyId = req.getFacultyId().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with email '" + email + "' is already registered");
        }
        if (userRepository.existsByUserCodeAndRole(facultyId, Role.FACULTY)) {
            throw new DuplicateResourceException("Faculty ID '" + facultyId + "' is already registered");
        }

        Department dept = resolveDepartment(req.getDepartment());
        String passwordHash = passwordEncoder.encode(req.getPassword());

        User user = new User(facultyId, Role.FACULTY, req.getFullName().trim(), email, passwordHash, dept);
        user.setPhone(req.getPhone().trim());
        User savedUser = userRepository.save(user);

        String token = tokenProvider.generateToken(savedUser);
        return mapToAuthResponse(savedUser, token);
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public void clearRateLimitMap() {
        this.forgotPasswordRateLimitMap.clear();
    }

    private byte[] getHmacKey() {
        return (jwtSecret != null && !jwtSecret.isBlank())
                ? jwtSecret.getBytes(StandardCharsets.UTF_8)
                : "IntelliCampusDefaultSecretKeyForHmacSigning1234567890".getBytes(StandardCharsets.UTF_8);
    }

    public String computeHmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(getHmacKey(), "HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(bytes);
        } catch (Exception ex) {
            throw new RuntimeException("HMAC computation failed", ex);
        }
    }

    /**
     * Register a new Mentor
     */
    public AuthResponse registerMentor(MentorRegisterRequest req) {
        validateRegistrationFields(req.getFullName(), req.getMentorId(), req.getEmail(),
                req.getPhone(), req.getPassword(), req.getConfirmPassword());

        String email = req.getEmail().trim().toLowerCase();
        String mentorId = req.getMentorId().trim();

        if (!MENTOR_ID_PATTERN.matcher(mentorId).matches()) {
            throw new ValidationException("Mentor Employee ID must follow the pattern MEN-xxx (e.g. MEN-123)");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with email '" + email + "' is already registered");
        }
        if (userRepository.existsByUserCode(mentorId) || userRepository.existsByUserCodeAndRole(mentorId, Role.MENTOR)) {
            throw new DuplicateResourceException("Employee ID '" + mentorId + "' is already registered");
        }

        Department dept = resolveDepartment(req.getDepartment());
        String passwordHash = passwordEncoder.encode(req.getPassword());

        User user = new User(mentorId, Role.MENTOR, req.getFullName().trim(), email, passwordHash, dept);
        user.setPhone(req.getPhone().trim());
        if (req.getSpecialization() != null && !req.getSpecialization().isBlank()) {
            user.setSpecialization(req.getSpecialization().trim());
        }
        User savedUser = userRepository.save(user);

        String token = tokenProvider.generateToken(savedUser);
        return mapToAuthResponse(savedUser, token);
    }

    /**
     * Faculty Login
     */
    public AuthResponse loginFaculty(LoginRequest req) {
        return authenticateUser(req.getIdentifier(), req.getPassword(), Role.FACULTY);
    }

    /**
     * Mentor Login
     */
    public AuthResponse loginMentor(LoginRequest req) {
        return authenticateUser(req.getIdentifier(), req.getPassword(), Role.MENTOR);
    }

    /**
     * Common authentication preventing cross-role login and leaking account status
     */
    private AuthResponse authenticateUser(String identifier, String rawPassword, Role expectedRole) {
        if (identifier == null || rawPassword == null) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String lookup = identifier.trim();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(lookup)
                .or(() -> userRepository.findByUserCode(lookup));
        if (userOpt.isEmpty()) {
            // Throw generic error to prevent enumeration
            throw new InvalidCredentialsException("Invalid credentials");
        }

        User user = userOpt.get();

        // Cross-role verification: A faculty account must NOT be able to log in as mentor, and vice-versa
        if (user.getRole() != expectedRole) {
            log.warn("Cross-role login blocked: User '{}' with role {} attempted {} login",
                    user.getEmail(), user.getRole(), expectedRole);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String token = tokenProvider.generateToken(user);
        return mapToAuthResponse(user, token);
    }

    /**
     * Logout: blacklists token by storing its SHA-256 hash in revoked_tokens table
     */
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) return;

        try {
            if (tokenProvider.validateToken(token)) {
                String tokenHash = DigestUtils.sha256Hex(token);
                Date expDate = tokenProvider.getExpirationDateFromToken(token);
                LocalDateTime expiresAt = expDate != null
                        ? expDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                        : LocalDateTime.now().plusHours(1);

                if (!revokedTokenRepository.existsByTokenHash(tokenHash)) {
                    revokedTokenRepository.save(new RevokedToken(tokenHash, expiresAt));
                    log.info("Token successfully revoked and added to blacklist");
                }
            }
        } catch (Exception ex) {
            log.warn("Error during token revocation: {}", ex.getMessage());
        }
    }

    /**
     * Faculty Forgot Password:
     * Generates 6-digit OTP, stores HMAC-SHA256, enforces 60s cooldown per email,
     * restricts to FACULTY accounts, dispatches email via EmailService.
     */
    public ApiResponse requestFacultyForgotPassword(ForgotPasswordRequest req) {
        String genericSuccessMessage = "If this email is registered in our system, a 6-digit password reset code has been dispatched to your inbox.";

        if (req == null || req.getEmail() == null || !EMAIL_PATTERN.matcher(req.getEmail().trim()).matches()) {
            return new ApiResponse(true, genericSuccessMessage);
        }

        String email = req.getEmail().trim().toLowerCase();

        // 60-second cooldown per email on backend (checked before user lookup to avoid enumeration)
        LocalDateTime lastReq = forgotPasswordRateLimitMap.get(email);
        if (lastReq != null && lastReq.isAfter(LocalDateTime.now().minusSeconds(60))) {
            log.warn("Faculty forgot-password 60-second cooldown triggered for '{}'", email);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait 60 seconds before requesting another verification code.");
        }
        forgotPasswordRateLimitMap.put(email, LocalDateTime.now());

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            log.info("Faculty forgot-password requested for unregistered email: '{}'", email);
            return new ApiResponse(true, genericSuccessMessage);
        }

        User user = userOpt.get();

        // Cross-role verification: A faculty OTP only works for FACULTY accounts
        if (user.getRole() != Role.FACULTY) {
            log.warn("Forgot password role mismatch: User '{}' has role {}, not FACULTY", email, user.getRole());
            return new ApiResponse(true, genericSuccessMessage);
        }

        if (user.getStatus() != null && user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Faculty forgot-password requested for inactive account '{}' (status: {})", email, user.getStatus());
        }

        // Invalidate any older active tokens for this user
        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user);

        // Generate cryptographically secure 6-digit OTP
        int otpCode = 100000 + secureRandom.nextInt(900000);
        String otp = String.valueOf(otpCode);

        // Store HMAC-SHA256(otp) in database
        String otpHmac = computeHmacSha256(otp);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        PasswordResetToken resetToken = new PasswordResetToken(user, otpHmac, expiresAt);
        passwordResetTokenRepository.save(resetToken);

        // Dispatch email via SMTP
        try {
            emailService.sendPasswordResetOtp(user.getEmail(), otp);
            log.info("Password reset OTP email successfully sent to '{}'", user.getEmail());
        } catch (ResponseStatusException rse) {
            log.error("SMTP delivery failed: {}", rse.getReason());
            throw rse;
        } catch (Exception ex) {
            String reason = ex.getMessage();
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                reason = ex.getCause().getMessage();
            }
            log.error("SMTP delivery failed: {}", reason, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to dispatch verification code: " + (reason != null ? reason : "SMTP delivery error"));
        }

        return new ApiResponse(true, "A 6-digit password reset code has been dispatched to your registered institutional email.");
    }

    /**
     * Faculty Verify OTP:
     * Validates 6-digit OTP against HMAC-SHA256 using constant-time comparison.
     * Enforces max 5 attempts and 10-minute validity.
     * Returns a short-lived single-use reset token only if the OTP is correct.
     */
    public ApiResponse verifyFacultyOtp(VerifyOtpRequest req) {
        if (req == null || req.getEmail() == null || req.getOtp() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and 6-digit OTP are required.");
        }

        String email = req.getEmail().trim().toLowerCase();
        String enteredOtp = req.getOtp().trim();

        if (!enteredOtp.matches("^\\d{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP must be exactly 6 digits.");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP."));

        if (user.getRole() != Role.FACULTY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP.");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP."));

        // Check if maximum wrong attempts reached
        if (resetToken.getAttempts() >= 5) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. This OTP has been invalidated. Please request a new OTP.");
        }

        // Check 10-minute expiry
        if (resetToken.isExpired()) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OTP has expired (10-minute validity). Please request a new OTP.");
        }

        // Constant-time HMAC comparison
        String enteredHmac = computeHmacSha256(enteredOtp);
        byte[] storedBytes = resetToken.getTokenHash().getBytes(StandardCharsets.UTF_8);
        byte[] enteredBytes = enteredHmac.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(storedBytes, enteredBytes)) {
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

        // Generate short-lived single-use reset token
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String singleUseResetToken = Hex.encodeHexString(randomBytes);
        String resetTokenHash = DigestUtils.sha256Hex(singleUseResetToken);

        // Store resetTokenHash and invalidate raw OTP so it cannot be reused
        resetToken.setResetTokenHash(resetTokenHash);
        resetToken.setTokenHash("VERIFIED_" + resetToken.getId());
        passwordResetTokenRepository.save(resetToken);

        Map<String, Object> data = Map.of("resetToken", singleUseResetToken);
        return new ApiResponse(true, "OTP verified successfully. You may now set a new password.", data);
    }

    /**
     * Faculty Reset Password:
     * Requires single-use reset token from verify-otp, validates password complexity,
     * updates password with BCrypt, and invalidates all reset tokens.
     */
    public ApiResponse resetFacultyPassword(ResetPasswordRequest req) {
        if (req == null || req.getToken() == null || req.getToken().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token is required.");
        }
        if (req.getNewPassword() == null || !PASSWORD_PATTERN.matcher(req.getNewPassword()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters with uppercase, lowercase, and a number.");
        }
        if (req.getConfirmPassword() != null && !req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }

        String rawToken = req.getToken().trim();
        String resetTokenHash = DigestUtils.sha256Hex(rawToken);

        PasswordResetToken resetToken = passwordResetTokenRepository.findByResetTokenHashAndUsedFalse(resetTokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid or expired password reset session. Please request a new OTP."));

        if (resetToken.isExpired()) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password reset session has expired. Please request a new OTP.");
        }

        User user = resetToken.getUser();
        if (user.getRole() != Role.FACULTY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset session for faculty portal.");
        }

        // Update password with BCrypt
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Invalidate all tokens for user
        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user);

        log.info("Password successfully reset for faculty '{}'", user.getEmail());
        return new ApiResponse(true, "Password has been successfully updated. You may now sign in.");
    }

    /**
     * Mentor Forgot Password:
     * Generates 6-digit OTP, stores HMAC-SHA256, enforces 60s cooldown per email,
     * restricts to MENTOR accounts, dispatches email via EmailService.
     */
    public ApiResponse requestMentorForgotPassword(ForgotPasswordRequest req) {
        String genericSuccessMessage = "If this email is registered in our system, a 6-digit password reset code has been dispatched to your inbox.";

        if (req == null || req.getEmail() == null || !EMAIL_PATTERN.matcher(req.getEmail().trim()).matches()) {
            return new ApiResponse(true, genericSuccessMessage);
        }

        String email = req.getEmail().trim().toLowerCase();

        // 60-second cooldown per email on backend (checked before user lookup to avoid enumeration)
        LocalDateTime lastReq = forgotPasswordRateLimitMap.get(email);
        if (lastReq != null && lastReq.isAfter(LocalDateTime.now().minusSeconds(60))) {
            log.warn("Mentor forgot-password 60-second cooldown triggered for '{}'", email);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait 60 seconds before requesting another verification code.");
        }
        forgotPasswordRateLimitMap.put(email, LocalDateTime.now());

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            return new ApiResponse(true, genericSuccessMessage);
        }

        User user = userOpt.get();

        // A mentor OTP only works for MENTOR accounts
        if (user.getRole() != Role.MENTOR) {
            log.warn("Forgot password role mismatch: User '{}' has role {}, not MENTOR", email, user.getRole());
            return new ApiResponse(true, genericSuccessMessage);
        }

        if (user.getStatus() != null && user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Mentor forgot-password requested for inactive account '{}' (status: {})", email, user.getStatus());
        }

        // Invalidate any older active tokens for this user
        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user);

        // Generate cryptographically secure 6-digit OTP
        int otpCode = 100000 + secureRandom.nextInt(900000);
        String otp = String.valueOf(otpCode);

        // Store HMAC-SHA256(otp) in database
        String otpHmac = computeHmacSha256(otp);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        PasswordResetToken resetToken = new PasswordResetToken(user, otpHmac, expiresAt);
        passwordResetTokenRepository.save(resetToken);

        // Dispatch email
        try {
            emailService.sendPasswordResetOtp(user.getEmail(), otp);
            log.info("Password reset OTP email successfully sent to '{}'", user.getEmail());
        } catch (ResponseStatusException rse) {
            log.error("SMTP delivery failed: {}", rse.getReason());
            throw rse;
        } catch (Exception ex) {
            String reason = ex.getMessage();
            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                reason = ex.getCause().getMessage();
            }
            log.error("SMTP delivery failed: {}", reason, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to dispatch verification code: " + (reason != null ? reason : "SMTP delivery error"));
        }

        return new ApiResponse(true, "A 6-digit password reset code has been dispatched to your registered institutional email.");
    }

    /**
     * Mentor Verify OTP:
     * Validates 6-digit OTP against HMAC-SHA256 using constant-time comparison.
     * Enforces max 5 attempts and 10-minute validity.
     * Returns a short-lived single-use reset token only if the OTP is correct.
     */
    public ApiResponse verifyMentorOtp(VerifyOtpRequest req) {
        if (req == null || req.getEmail() == null || req.getOtp() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and 6-digit OTP are required.");
        }

        String email = req.getEmail().trim().toLowerCase();
        String enteredOtp = req.getOtp().trim();

        if (!enteredOtp.matches("^\\d{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP must be exactly 6 digits.");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP."));

        if (user.getRole() != Role.MENTOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP.");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findFirstByUserAndUsedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP."));

        // Check if maximum wrong attempts reached
        if (resetToken.getAttempts() >= 5) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. This OTP has been invalidated. Please request a new OTP.");
        }

        // Check 10-minute expiry
        if (resetToken.isExpired()) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OTP has expired (10-minute validity). Please request a new OTP.");
        }

        // Constant-time HMAC comparison
        String enteredHmac = computeHmacSha256(enteredOtp);
        byte[] storedBytes = resetToken.getTokenHash().getBytes(StandardCharsets.UTF_8);
        byte[] enteredBytes = enteredHmac.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(storedBytes, enteredBytes)) {
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

        // Generate short-lived single-use reset token
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String singleUseResetToken = Hex.encodeHexString(randomBytes);
        String resetTokenHash = DigestUtils.sha256Hex(singleUseResetToken);

        // Store resetTokenHash and invalidate raw OTP so it cannot be reused
        resetToken.setResetTokenHash(resetTokenHash);
        resetToken.setTokenHash("VERIFIED_" + resetToken.getId());
        passwordResetTokenRepository.save(resetToken);

        Map<String, Object> data = Map.of("resetToken", singleUseResetToken);
        return new ApiResponse(true, "OTP verified successfully. You may now set a new password.", data);
    }

    /**
     * Mentor Reset Password:
     * Requires single-use reset token from verify-otp, validates password complexity,
     * updates password with BCrypt, and invalidates all reset tokens.
     */
    public ApiResponse resetMentorPassword(ResetPasswordRequest req) {
        if (req == null || req.getToken() == null || req.getToken().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token is required.");
        }
        if (req.getNewPassword() == null || !PASSWORD_PATTERN.matcher(req.getNewPassword()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters with uppercase, lowercase, and a number.");
        }
        if (req.getConfirmPassword() != null && !req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }

        String rawToken = req.getToken().trim();
        String resetTokenHash = DigestUtils.sha256Hex(rawToken);

        PasswordResetToken resetToken = passwordResetTokenRepository.findByResetTokenHashAndUsedFalse(resetTokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid or expired password reset session. Please request a new OTP."));

        if (resetToken.isExpired()) {
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password reset session has expired. Please request a new OTP.");
        }

        User user = resetToken.getUser();
        if (user.getRole() != Role.MENTOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset session for mentor portal.");
        }

        // Update password with BCrypt
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Invalidate all tokens for user
        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user);

        log.info("Password successfully reset for mentor '{}'", user.getEmail());
        return new ApiResponse(true, "Password has been successfully updated. You may now sign in.");
    }

    /**
     * Forgot Password: sends email with cryptographically secure random token
     * Always returns generic success message without leaking account existence
     */
    public ApiResponse requestForgotPassword(ForgotPasswordRequest req) {
        String genericSuccessMessage = "If this email is registered in our system, a password reset link has been dispatched to your inbox.";

        if (req == null || req.getEmail() == null || !EMAIL_PATTERN.matcher(req.getEmail().trim()).matches()) {
            return new ApiResponse(true, genericSuccessMessage);
        }

        String email = req.getEmail().trim().toLowerCase();

        // Basic rate limiting: Allow 1 request per 30 seconds per email
        LocalDateTime lastReq = forgotPasswordRateLimitMap.get(email);
        if (lastReq != null && lastReq.isAfter(LocalDateTime.now().minusSeconds(30))) {
            log.warn("Forgot password rate limit triggered for '{}'", email);
            return new ApiResponse(true, genericSuccessMessage);
        }
        forgotPasswordRateLimitMap.put(email, LocalDateTime.now());

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            return new ApiResponse(true, genericSuccessMessage);
        }

        User user = userOpt.get();

        // Invalidate any existing active tokens for this user
        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user);

        // Generate 32-byte cryptographically secure random token (URL-safe Base64 encoded)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Store SHA-256 hash in database
        String tokenHash = DigestUtils.sha256Hex(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

        PasswordResetToken resetToken = new PasswordResetToken(user, tokenHash, expiresAt);
        passwordResetTokenRepository.save(resetToken);

        // Dispatch email
        emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), rawToken, user.getRole().name());

        return new ApiResponse(true, genericSuccessMessage);
    }

    /**
     * Validate reset token before showing form
     */
    @Transactional(readOnly = true)
    public ApiResponse validateResetToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw new ValidationException("Reset token is required");
        }

        String tokenHash = DigestUtils.sha256Hex(rawToken.trim());
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            throw new ValidationException("Password reset link is invalid or does not exist");
        }

        PasswordResetToken token = tokenOpt.get();
        if (Boolean.TRUE.equals(token.getUsed())) {
            throw new ValidationException("This password reset link has already been used");
        }
        if (token.isExpired()) {
            throw new ValidationException("This password reset link has expired");
        }

        return new ApiResponse(true, "Reset token is valid");
    }

    /**
     * Reset Password using token
     */
    public ApiResponse resetPassword(ResetPasswordRequest req) {
        if (req == null || req.getToken() == null || req.getToken().trim().isEmpty()) {
            throw new ValidationException("Reset token is required");
        }
        if (req.getNewPassword() == null || !PASSWORD_PATTERN.matcher(req.getNewPassword()).matches()) {
            throw new ValidationException("Password must be at least 8 characters long and contain uppercase, lowercase, digit, and special character");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ValidationException("Passwords do not match");
        }

        String tokenHash = DigestUtils.sha256Hex(req.getToken().trim());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException("Password reset link is invalid or does not exist"));

        if (Boolean.TRUE.equals(token.getUsed())) {
            throw new ValidationException("This password reset link has already been used");
        }
        if (token.isExpired()) {
            throw new ValidationException("This password reset link has expired");
        }

        User user = token.getUser();
        // Update user password
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Mark current token as used
        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        // Invalidate any other tokens for this user
        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user);

        log.info("Password successfully reset for user '{}'", user.getEmail());
        return new ApiResponse(true, "Password has been successfully updated. You may now sign in.");
    }

    private void validateRegistrationFields(String name, String id, String email, String phone,
                                            String password, String confirmPassword) {
        if (name == null || name.trim().length() < 3) {
            throw new ValidationException("Full name must be at least 3 characters long");
        }
        if (id == null || id.trim().length() < 2) {
            throw new ValidationException("Employee/Registration ID is required");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("A valid official email address is required");
        }
        if (phone == null || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new ValidationException("Phone number must be exactly 10 digits");
        }
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ValidationException("Password must be at least 8 characters with uppercase, lowercase, and a number");
        }
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("Password and confirm password do not match");
        }
    }

    private Department resolveDepartment(String deptName) {
        if (deptName == null || deptName.trim().isEmpty()) {
            return null;
        }
        String clean = deptName.trim();
        return departmentRepository.findByName(clean)
                .orElseGet(() -> {
                    String rawCode = clean.toUpperCase().replaceAll("[^A-Z0-9_]", "_");
                    String code = rawCode.length() > 50 ? rawCode.substring(0, 50) : rawCode;
                    return departmentRepository.findByCode(code)
                            .orElseGet(() -> departmentRepository.save(new Department(code, clean)));
                });
    }

    private AuthResponse mapToAuthResponse(User user, String token) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getUserCode(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getDepartment() != null ? user.getDepartment().getName() : "General",
                user.getPhone(),
                user.getSpecialization()
        );
    }
}
