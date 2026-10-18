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
import com.intellicampus.user.repository.PasswordResetTokenRepository;
import com.intellicampus.user.repository.RevokedTokenRepository;
import com.intellicampus.user.repository.UserRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Password complexity pattern: min 8 chars, 1 uppercase, 1 lowercase, 1 digit
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;

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

    /**
     * Register a new Mentor
     */
    public AuthResponse registerMentor(MentorRegisterRequest req) {
        validateRegistrationFields(req.getFullName(), req.getMentorId(), req.getEmail(),
                req.getPhone(), req.getPassword(), req.getConfirmPassword());

        String email = req.getEmail().trim().toLowerCase();
        String mentorId = req.getMentorId().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with email '" + email + "' is already registered");
        }
        if (userRepository.existsByUserCodeAndRole(mentorId, Role.MENTOR)) {
            throw new DuplicateResourceException("Mentor ID '" + mentorId + "' is already registered");
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
