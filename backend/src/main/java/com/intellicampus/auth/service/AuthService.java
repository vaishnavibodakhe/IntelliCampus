package com.intellicampus.auth.service;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.auth.dto.*;
import com.intellicampus.auth.model.PasswordResetToken;
import com.intellicampus.auth.repository.PasswordResetTokenRepository;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.FaceEnrollmentService;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final long MAX_PHOTO_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_PHOTO_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png");

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final DivisionRepository divisionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final FaceEnrollmentService faceEnrollmentService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final boolean demoResetCodeVisible;

    public AuthService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            DepartmentRepository departmentRepository,
            DivisionRepository divisionRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            FaceEnrollmentService faceEnrollmentService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.auth.demo-reset-code-visible:true}") boolean demoResetCodeVisible) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.departmentRepository = departmentRepository;
        this.divisionRepository = divisionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.faceEnrollmentService = faceEnrollmentService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.demoResetCodeVisible = demoResetCodeVisible;
    }

    public AuthResponseDto login(LoginRequestDto req) {
        if (req == null || req.getUserCode() == null || req.getUserCode().isBlank() ||
            req.getPassword() == null || req.getPassword().isBlank()) {
            throw new BadCredentialsException("Invalid login code or password.");
        }

        String code = req.getUserCode().trim();
        Optional<User> userOpt = userRepository.findByUserCode(code);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findAll().stream()
                    .filter(u -> u.getUserCode() != null && u.getUserCode().equalsIgnoreCase(code))
                    .findFirst();
        }

        if (userOpt.isEmpty()) {
            throw new BadCredentialsException("Invalid login code or password.");
        }

        User user = userOpt.get();

        // 1. Role / portal verification: default to STUDENT if portal/expectedRole is omitted.
        // The login succeeds only if user.role matches expectedRole.
        // For any mismatch, return generic error ("Invalid login code or password.") to avoid role leakage,
        // and record it as a failed attempt.
        Role expectedRole = Role.STUDENT;
        String requestedRoleStr = req.getExpectedRole();
        if (requestedRoleStr != null && !requestedRoleStr.isBlank()) {
            try {
                expectedRole = Role.valueOf(requestedRoleStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadCredentialsException("Invalid login code or password.");
            }
        }

        if (user.getRole() != expectedRole) {
            int failed = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(failed);
            if (failed >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
                log.warn("User {} login rejected for expected role {} and locked out after 5 consecutive failed login attempts.", user.getUserCode(), expectedRole);
            } else {
                log.warn("User {} login rejected (actual role: {}, expected portal: {}). Attempt {} counted as failed.", user.getUserCode(), user.getRole(), expectedRole, failed);
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid login code or password.");
        }

        // 2. Check account lockout
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException("Account temporarily locked due to too many failed attempts. Please try again later.");
        }

        // 3. Validate password
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            int failed = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(failed);
            if (failed >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
                log.warn("User {} locked out for 5 minutes after 5 consecutive failed login attempts.", user.getUserCode());
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid login code or password.");
        }

        // 3. Reset failed attempts upon successful authentication
        if (user.getFailedAttempts() != null && user.getFailedAttempts() > 0) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        // 4. Generate JWT Token
        String token = jwtTokenProvider.generateToken(user);
        UserSummaryDto summary = buildUserSummary(user);

        return new AuthResponseDto(token, jwtTokenProvider.getExpirationSeconds(), summary);
    }

    @Transactional
    public UserSummaryDto register(RegisterRequestDto req, MultipartFile photo) {
        validateRegistrationFields(req);
        validatePhoto(photo);

        // 1. Resolve or create Department
        Department dept = null;
        if (req.getDepartment() != null && !req.getDepartment().isBlank()) {
            dept = departmentRepository.findByName(req.getDepartment())
                    .or(() -> departmentRepository.findByCode(req.getDepartment()))
                    .orElseGet(() -> departmentRepository.save(
                            new Department(req.getDepartment().toUpperCase().replace(" ", "_"), req.getDepartment())
                    ));
        }

        // 2. Resolve or create Division
        Division div = null;
        if (req.getDivision() != null && !req.getDivision().isBlank()) {
            final Department targetDept = dept;
            Long deptId = targetDept != null ? targetDept.getId() : null;
            if (deptId != null) {
                div = divisionRepository.findByNameAndDepartmentId(req.getDivision(), deptId)
                        .orElseGet(() -> {
                            int yr = 3;
                            if (req.getYear() != null && req.getYear().replaceAll("[^0-9]", "").length() > 0) {
                                try {
                                    yr = Integer.parseInt(req.getYear().replaceAll("[^0-9]", ""));
                                } catch (Exception ignored) {}
                            }
                            return divisionRepository.save(new Division(targetDept, yr, req.getDivision()));
                        });
            }
        }

        // 3. Hash Password
        String passwordHash = passwordEncoder.encode(req.getPassword());

        // 4. Create and persist User entity
        User user = new User();
        user.setUserCode(req.getLoginCode().trim());
        user.setRole(Role.STUDENT);
        user.setName(req.getName().trim());
        user.setEmail(req.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordHash);
        user.setDepartment(dept);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        // 5. Create and persist StudentProfile entity
        StudentProfile profile = new StudentProfile();
        profile.setUser(savedUser);
        profile.setRollNo(req.getRollNo().trim());
        profile.setDivision(div);
        profile.setMentor(null);
        profile.setFaceEnrolled(false);
        profile.setAttendanceRate(0.0);
        profile.setBacklogs(0);
        profile.setFeeStatus("PAID");
        profile.setDueAmount(0.0);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        StudentProfile savedProfile = studentProfileRepository.save(profile);

        // 6. Enroll student face (transactional: if AI enrollment fails, transaction rolls back)
        StudentProfile enrolledProfile = faceEnrollmentService.enrollStudentFace(
                savedProfile.getId(),
                photo,
                req.getConsentVersion() != null ? req.getConsentVersion() : "1.0"
        );

        return buildUserSummary(savedUser, enrolledProfile);
    }

    public ForgotPasswordResponseDto forgotPassword(ForgotPasswordRequestDto req) {
        final String neutralMessage = "If the account exists, a reset code has been generated.";
        if (req == null || req.getIdentifier().isBlank()) {
            return new ForgotPasswordResponseDto(neutralMessage);
        }

        String identifier = req.getIdentifier().trim();
        Optional<User> userOpt = userRepository.findByUserCode(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(identifier.toLowerCase());
        }
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findAll().stream()
                    .filter(u -> (u.getUserCode() != null && u.getUserCode().equalsIgnoreCase(identifier)) ||
                                 (u.getEmail() != null && u.getEmail().equalsIgnoreCase(identifier)))
                    .findFirst();
        }

        // Constant-time behavior / neutral response if user does not exist
        if (userOpt.isEmpty()) {
            return new ForgotPasswordResponseDto(neutralMessage);
        }

        User user = userOpt.get();

        // Invalidate previous active reset codes for this user
        List<PasswordResetToken> existing = passwordResetTokenRepository.findByUserAndUsedFalse(user);
        for (PasswordResetToken t : existing) {
            t.setUsed(true);
        }
        passwordResetTokenRepository.saveAll(existing);

        // Generate 6-digit one-time code
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        String codeHash = DigestUtils.sha256Hex(code);

        PasswordResetToken token = new PasswordResetToken(user, codeHash, LocalDateTime.now().plusMinutes(15));
        passwordResetTokenRepository.save(token);

        log.info("[DEMO AUTH] Generated 6-digit password reset code for user {}: {}", user.getUserCode(), code);

        if (demoResetCodeVisible) {
            return new ForgotPasswordResponseDto(neutralMessage, code);
        }
        return new ForgotPasswordResponseDto(neutralMessage);
    }

    @Transactional
    public ResetPasswordResponseDto resetPassword(ResetPasswordRequestDto req) {
        if (req == null) {
            throw new IllegalArgumentException("Reset request is required.");
        }

        String code = req.getCode();
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Reset code must be a valid 6-digit numerical code.");
        }

        if (req.getNewPassword() == null || !PASSWORD_PATTERN.matcher(req.getNewPassword()).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters long and contain at least one letter and one number.");
        }

        if (req.getConfirmPassword() != null && !req.getConfirmPassword().equals(req.getNewPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        PasswordResetToken token = null;
        String inputHash = DigestUtils.sha256Hex(code);

        // If user identifier was provided, find through user's active token
        String userIdentifier = null;
        if (req.getLoginCode() != null && !req.getLoginCode().isBlank()) {
            userIdentifier = req.getLoginCode().trim();
        } else if (req.getEmail() != null && !req.getEmail().isBlank()) {
            userIdentifier = req.getEmail().trim();
        }

        if (userIdentifier != null) {
            final String ident = userIdentifier;
            Optional<User> userOpt = userRepository.findByUserCode(ident)
                    .or(() -> userRepository.findByEmail(ident.toLowerCase()));

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(user);
                if (tokenOpt.isEmpty()) {
                    throw new IllegalArgumentException("Invalid or expired reset code.");
                }
                token = tokenOpt.get();

                if (token.getAttempts() >= 5) {
                    token.setUsed(true);
                    passwordResetTokenRepository.save(token);
                    throw new IllegalArgumentException("Maximum reset attempts exceeded (5/5). Please request a new code.");
                }

                if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
                    throw new IllegalArgumentException("Reset code has expired. Please request a new code.");
                }

                if (!inputHash.equals(token.getTokenHash())) {
                    int attempts = token.getAttempts() + 1;
                    token.setAttempts(attempts);
                    if (attempts >= 5) {
                        token.setUsed(true);
                    }
                    passwordResetTokenRepository.save(token);
                    throw new IllegalArgumentException("Invalid reset code. Attempt " + attempts + " of 5.");
                }
            }
        }

        // If not found via user identifier, look up by token hash directly
        if (token == null) {
            Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenHashAndUsedFalse(inputHash);
            if (tokenOpt.isEmpty()) {
                Optional<PasswordResetToken> usedOpt = passwordResetTokenRepository.findByTokenHash(inputHash);
                if (usedOpt.isPresent() && Boolean.TRUE.equals(usedOpt.get().getUsed())) {
                    throw new IllegalArgumentException("Reset code has already been used.");
                }
                throw new IllegalArgumentException("Invalid or expired reset code.");
            }
            token = tokenOpt.get();

            if (token.getAttempts() >= 5) {
                token.setUsed(true);
                passwordResetTokenRepository.save(token);
                throw new IllegalArgumentException("Maximum reset attempts exceeded (5/5). Please request a new code.");
            }

            if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("Reset code has expired. Please request a new code.");
            }
        }

        // Update password and reset lockout counters
        User targetUser = token.getUser();
        targetUser.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        targetUser.setMustChangePassword(false);
        targetUser.setFailedAttempts(0);
        targetUser.setLockedUntil(null);
        userRepository.save(targetUser);

        // Invalidate all tokens for this user
        List<PasswordResetToken> allTokens = passwordResetTokenRepository.findByUser(targetUser);
        for (PasswordResetToken t : allTokens) {
            t.setUsed(true);
        }
        passwordResetTokenRepository.saveAll(allTokens);

        log.info("Password successfully reset for user {}", targetUser.getUserCode());
        return new ResetPasswordResponseDto(true, "Password reset successfully. You can now log in with your new password.");
    }

    private void validateRegistrationFields(RegisterRequestDto req) {
        if (req == null) {
            throw new IllegalArgumentException("Registration payload is required.");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Full name is mandatory.");
        }
        if (req.getEmail() == null || req.getEmail().isBlank() || !EMAIL_PATTERN.matcher(req.getEmail().trim()).matches()) {
            throw new IllegalArgumentException("A valid institutional email address is mandatory.");
        }
        if (req.getLoginCode() == null || req.getLoginCode().isBlank()) {
            throw new IllegalArgumentException("Login code is mandatory.");
        }
        if (req.getRollNo() == null || req.getRollNo().isBlank()) {
            throw new IllegalArgumentException("Roll number is mandatory.");
        }
        if (req.getDepartment() == null || req.getDepartment().isBlank()) {
            throw new IllegalArgumentException("Department is mandatory.");
        }
        if (req.getDivision() == null || req.getDivision().isBlank()) {
            throw new IllegalArgumentException("Division is mandatory.");
        }
        if (req.getPassword() == null || !PASSWORD_PATTERN.matcher(req.getPassword()).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters long and contain at least one letter and one number.");
        }
        if (req.getConfirmPassword() != null && !req.getConfirmPassword().equals(req.getPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (!Boolean.TRUE.equals(req.getBiometricConsent())) {
            throw new IllegalArgumentException("Biometric consent is mandatory for student registration.");
        }

        // Uniqueness checks
        if (userRepository.existsByUserCode(req.getLoginCode().trim())) {
            throw new IllegalArgumentException("Login code already in use: " + req.getLoginCode().trim());
        }
        if (userRepository.existsByEmail(req.getEmail().trim().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered: " + req.getEmail().trim());
        }
        if (studentProfileRepository.existsByRollNo(req.getRollNo().trim())) {
            throw new IllegalArgumentException("Roll number already registered: " + req.getRollNo().trim());
        }
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Face photo is mandatory for biometric attendance registration.");
        }
        if (photo.getSize() > MAX_PHOTO_SIZE) {
            throw new IllegalArgumentException("Photo file size exceeds maximum allowable limit of 5 MB.");
        }
        String contentType = photo.getContentType();
        if (contentType == null || !ALLOWED_PHOTO_TYPES.contains(contentType.toLowerCase())) {
            String name = photo.getOriginalFilename() != null ? photo.getOriginalFilename().toLowerCase() : "";
            if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")) {
                throw new IllegalArgumentException("Invalid file format. Only JPEG and PNG images are supported.");
            }
        }
    }

    public UserSummaryDto buildUserSummary(User u) {
        Optional<StudentProfile> profOpt = studentProfileRepository.findByUser(u);
        return buildUserSummary(u, profOpt.orElse(null));
    }

    public UserSummaryDto buildUserSummary(User u, StudentProfile p) {
        UserSummaryDto dto = new UserSummaryDto(
                u.getId(),
                u.getUserCode(),
                u.getName(),
                u.getEmail(),
                u.getRole(),
                u.getDepartment() != null ? u.getDepartment().getName() : "General"
        );

        if (p != null) {
            dto.setRollNo(p.getRollNo());
            dto.setDivision(p.getDivision() != null ? p.getDivision().getName() : null);
            dto.setAttendanceRate(p.getAttendanceRate());
            dto.setFeeStatus(p.getFeeStatus());
            dto.setFaceEnrolled(Boolean.TRUE.equals(p.getFaceEnrolled()));
        }
        return dto;
    }

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }
}
