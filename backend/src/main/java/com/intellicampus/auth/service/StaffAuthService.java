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

    @Value("${app.security.admin-otp-enabled:false}")
    private boolean adminOtpEnabled = false;

    @Value("${SETUP_TOKEN:${setup.token:}}")
    private String setupToken = "";

    public void setSetupToken(String setupToken) {
        this.setupToken = setupToken;
    }

    public void setAdminOtpEnabled(boolean adminOtpEnabled) {
        this.adminOtpEnabled = adminOtpEnabled;
    }

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetRateLimitRepository rateLimitRepository;
    private final AuditChainService auditChainService;
    private final EmailService emailService;
    private final AuthRateLimiter authRateLimiter;

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
        this(userRepository, departmentRepository, passwordEncoder, jwtTokenProvider,
                passwordResetTokenRepository, refreshTokenRepository, rateLimitRepository,
                auditChainService, emailService, new AuthRateLimiter());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StaffAuthService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetRateLimitRepository rateLimitRepository,
            AuditChainService auditChainService,
            EmailService emailService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AuthRateLimiter authRateLimiter) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.rateLimitRepository = rateLimitRepository;
        this.auditChainService = auditChainService;
        this.emailService = emailService;
        this.authRateLimiter = authRateLimiter != null ? authRateLimiter : new AuthRateLimiter();
    }

    public boolean canBootstrapPrincipal() {
        return userRepository.countByRole(Role.PRINCIPAL) == 0;
    }

    public boolean canBootstrapAdmin() {
        return canBootstrapPrincipal();
    }

    public PendingStaffApprovalDto registerStaff(StaffRegisterRequestDto req) {
        String email = req.getEmail().trim().toLowerCase();
        Role role = req.getRole();

        // Self-registration is strictly restricted to one-time PRINCIPAL bootstrap
        if (role != Role.PRINCIPAL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Public registration is disabled for staff roles. Staff accounts must be created by a Principal.");
        }

        if (userRepository.countByRole(Role.PRINCIPAL) > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Principal registration is closed. Additional staff must be provisioned by an existing Principal.");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered: " + email);
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

        String baseCode = "PRIN-"
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
                "Initial Principal account bootstrapped successfully"
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

    private static final String DUMMY_BCRYPT_HASH = "$2a$12$e8xOQjZc1zF6W6qf1D0JMeZ8L9u2F1A7E9b8C0d1E2F3a4B5c6D7e";

    public PendingStaffApprovalDto createStaffByAdmin(AdminCreateStaffRequestDto req, User adminUser) {
        String email = req.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered: " + email);
        }

        Role role = req.getRole();
        if (role == Role.STUDENT || role == Role.PUBLIC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff creation only supports administrative and academic roles.");
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

        String baseCode = (role == Role.PRINCIPAL ? "PRIN-" : role == Role.HOD ? "HOD-" : role == Role.MENTOR ? "MEN-" : "FAC-")
                + (req.getEmployeeCode() != null && !req.getEmployeeCode().trim().isEmpty()
                   ? req.getEmployeeCode().trim().toUpperCase().replaceAll("[^A-Z0-9]", "-")
                   : Math.abs(email.hashCode() % 10000));

        String userCode = baseCode;
        int suffix = 1;
        while (userRepository.existsByUserCode(userCode)) {
            userCode = baseCode + "-" + suffix++;
        }

        // Random unusable password hash (Admin never enters, sees or types password)
        String unusablePassword = java.util.UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        String passwordHash = passwordEncoder.encode(unusablePassword);

        User user = new User(userCode, role, req.getName().trim(), email, passwordHash, department);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        user.setFailedAttempts(0);
        User savedUser = userRepository.save(user);

        // Generate 24-hour setup OTP (000000 - 999999)
        int otpNum = secureRandom.nextInt(1_000_000);
        String rawOtp = String.format("%06d", otpNum);
        String tokenHash = DigestUtils.sha256Hex(rawOtp);

        passwordResetTokenRepository.invalidateAllForUser(savedUser);
        PasswordResetToken setupToken = new PasswordResetToken(savedUser, tokenHash, LocalDateTime.now().plusHours(24));
        passwordResetTokenRepository.save(setupToken);

        // Audit Event: Staff Account Created by Principal
        auditChainService.recordEntry(
                adminUser.getId(),
                adminUser.getName(),
                "STAFF_CREATED_BY_ADMIN",
                "USER",
                savedUser.getId().toString(),
                "{}",
                "{\"email\":\"" + savedUser.getEmail() + "\",\"role\":\"" + savedUser.getRole() + "\",\"status\":\"ACTIVE\"}",
                "Staff account created by Principal " + adminUser.getName()
        );

        // Dispatch invite setup email containing 6-digit OTP (never log OTP)
        emailService.sendStaffAccountSetupOtp(savedUser.getEmail(), rawOtp, savedUser.getRole());

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

    public void resendStaffSetupOtp(Long staffId, User adminUser, String clientIp) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff user not found: " + staffId));

        if (staff.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot send setup invite to an inactive or disabled account.");
        }

        // Rate limiting check: max 3 requests per hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long emailCount = rateLimitRepository.countByEmailAndRequestedAtAfter(staff.getEmail(), oneHourAgo);
        long ipCount = rateLimitRepository.countByIpAddressAndRequestedAtAfter(clientIp, oneHourAgo);
        if (emailCount >= 3 || ipCount >= 3) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many setup OTP requests. Maximum 3 requests per hour are allowed.");
        }

        // Invalidate previous OTPs
        passwordResetTokenRepository.invalidateAllForUser(staff);

        // Generate fresh 24-hour setup OTP
        int otpNum = secureRandom.nextInt(1_000_000);
        String rawOtp = String.format("%06d", otpNum);
        String tokenHash = DigestUtils.sha256Hex(rawOtp);

        PasswordResetToken setupToken = new PasswordResetToken(staff, tokenHash, LocalDateTime.now().plusHours(24));
        passwordResetTokenRepository.save(setupToken);

        // Audit Event: Setup OTP Resent
        auditChainService.recordEntry(
                adminUser.getId(),
                adminUser.getName(),
                "STAFF_SETUP_OTP_RESENT",
                "USER",
                staff.getId().toString(),
                "{}",
                "{\"email\":\"" + staff.getEmail() + "\"}",
                "Staff setup OTP resent by Principal " + adminUser.getName()
        );

        rateLimitRepository.save(new PasswordResetRateLimit(staff.getEmail(), clientIp));

        // Dispatch email (never log OTP)
        emailService.sendStaffAccountSetupOtp(staff.getEmail(), rawOtp, staff.getRole());
    }

    public void deactivateStaff(Long staffId, User adminUser) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff user not found: " + staffId));

        if (staff.getId().equals(adminUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Principals cannot deactivate their own account.");
        }

        staff.setStatus(UserStatus.DISABLED);
        userRepository.save(staff);

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllForUser(staff);

        // Audit Event: Deactivation
        auditChainService.recordEntry(
                adminUser.getId(),
                adminUser.getName(),
                "STAFF_DEACTIVATED",
                "USER",
                staff.getId().toString(),
                "{\"status\":\"ACTIVE\"}",
                "{\"status\":\"DISABLED\"}",
                "Staff account deactivated by Principal " + adminUser.getName()
        );
    }

    public AuthResponseDto loginStaff(LoginRequestDto req) {
        return loginStaff(req, null);
    }

    public AuthResponseDto loginStaff(LoginRequestDto req, String endpointRole) {
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(req.getPassword() != null ? req.getPassword() : "", DUMMY_BCRYPT_HASH);
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userOpt.get();

        // Account lockout check
        if (user.getStatus() == UserStatus.LOCKED || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))) {
            throw new org.springframework.security.authentication.LockedException("Account is temporarily locked. Please try again later.");
        }

        // Check account status: non-active accounts (DISABLED, etc.) return generic error
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Strict role matching & persona boundaries
        if ("ADMIN".equalsIgnoreCase(endpointRole) || "PRINCIPAL".equalsIgnoreCase(endpointRole)) {
            if (user.getRole() != Role.PRINCIPAL) {
                throw new BadCredentialsException("Invalid email or password");
            }
        } else if ("STAFF".equalsIgnoreCase(endpointRole)) {
            // Staff portal: Principal cannot use staff page
            if (user.getRole() == Role.PRINCIPAL) {
                throw new BadCredentialsException("Invalid email or password");
            }
            // Strict role requested check: HOD cannot log in as Faculty, etc.
            if (req.getRole() != null && user.getRole() != req.getRole()) {
                throw new BadCredentialsException("Invalid email or password");
            }
            if (user.getRole() != Role.HOD && user.getRole() != Role.FACULTY && user.getRole() != Role.MENTOR) {
                throw new BadCredentialsException("Invalid email or password");
            }
        } else {
            // General or internal call: match role if specified
            if (req.getRole() != null && user.getRole() != req.getRole()) {
                throw new BadCredentialsException("Invalid email or password");
            }
        }

        // Verify BCrypt password
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            int attempts = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(attempts);

            if (attempts >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                userRepository.save(user);

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
                auditChainService.recordEntry(
                        user.getId(),
                        user.getName(),
                        "LOGIN_FAILED",
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

    public AuthResponseDto loginPrincipal(LoginRequestDto req) {
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";

        Optional<User> userOpt = userRepository.findByEmail(email);

        // Constant-time check: If user not found OR user is NOT a PRINCIPAL, run dummy BCrypt to prevent timing attacks,
        // and return the exact same generic BadCredentialsException so other roles get the identical 401.
        if (userOpt.isEmpty() || userOpt.get().getRole() != Role.PRINCIPAL) {
            passwordEncoder.matches(req.getPassword() != null ? req.getPassword() : "", DUMMY_BCRYPT_HASH);
            throw new BadCredentialsException("Invalid email or password");
        }

        User principal = userOpt.get();

        // Check Account Lockout
        if (principal.getLockedUntil() != null) {
            if (principal.getLockedUntil().isAfter(LocalDateTime.now())) {
                throw new LockedException("Account is temporarily locked due to consecutive failed attempts. Try again later.");
            } else {
                principal.setLockedUntil(null);
                principal.setFailedAttempts(0);
                if (principal.getStatus() == UserStatus.LOCKED) {
                    principal.setStatus(UserStatus.ACTIVE);
                }
            }
        }

        if (principal.getStatus() == UserStatus.DISABLED || principal.getStatus() == UserStatus.INACTIVE || principal.getStatus() == UserStatus.SUSPENDED) {
            throw new DisabledException("Account is disabled or suspended. Please contact system governance.");
        }

        if (principal.getStatus() == UserStatus.PENDING_APPROVAL || principal.getStatus() == UserStatus.PENDING) {
            throw new DisabledException("Principal account is pending email verification. Please verify your OTP to activate the account.");
        }

        // Verify BCrypt password
        if (!passwordEncoder.matches(req.getPassword(), principal.getPasswordHash())) {
            int attempts = (principal.getFailedAttempts() != null ? principal.getFailedAttempts() : 0) + 1;
            principal.setFailedAttempts(attempts);

            if (attempts >= 5) {
                principal.setStatus(UserStatus.LOCKED);
                principal.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                userRepository.save(principal);

                auditChainService.recordEntry(
                        principal.getId(),
                        principal.getName(),
                        "PRINCIPAL_ACCOUNT_LOCKED",
                        "USER",
                        principal.getId().toString(),
                        "{\"failedAttempts\":5}",
                        "{\"status\":\"LOCKED\"}",
                        "Principal account locked for 15 minutes due to 5 consecutive failed attempts"
                );
            } else {
                userRepository.save(principal);
                auditChainService.recordEntry(
                        principal.getId(),
                        principal.getName(),
                        "PRINCIPAL_LOGIN_FAILURE",
                        "USER",
                        principal.getId().toString(),
                        "{}",
                        "{\"email\":\"" + principal.getEmail() + "\",\"attempts\":" + attempts + "}",
                        "Failed principal login attempt"
                );
            }

            throw new BadCredentialsException("Invalid email or password");
        }

        // Reset failed attempts on success
        principal.setFailedAttempts(0);
        principal.setLockedUntil(null);
        userRepository.save(principal);

        if (adminOtpEnabled) {
            String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
            String tokenHash = DigestUtils.sha256Hex(otp);
            PasswordResetToken otpToken = new PasswordResetToken(principal, tokenHash, LocalDateTime.now().plusMinutes(5));
            passwordResetTokenRepository.save(otpToken);
            if (emailService != null && emailService.isMailConfigured()) {
                emailService.sendPasswordResetOtp(principal.getEmail(), otp);
            }
            return new AuthResponseDto(true, "A 6-digit one-time password has been sent to your administrator email address.");
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(principal, null);
        String rawRefreshToken = generateSecureRandomToken();
        String hashedRefreshToken = DigestUtils.sha256Hex(rawRefreshToken);
        refreshTokenRepository.revokeAllForUser(principal);
        LocalDateTime expDate = LocalDateTime.now().plusDays(7);
        refreshTokenRepository.save(new RefreshToken(principal, hashedRefreshToken, expDate));

        // Audit Event: Login Success
        auditChainService.recordEntry(
                principal.getId(),
                principal.getName(),
                "PRINCIPAL_LOGIN_SUCCESS",
                "USER",
                principal.getId().toString(),
                "{}",
                "{\"email\":\"" + principal.getEmail() + "\",\"role\":\"PRINCIPAL\"}",
                "Principal successfully logged in"
        );

        UserSummaryDto userSummary = new UserSummaryDto(
                principal.getId(),
                principal.getUserCode(),
                principal.getName(),
                principal.getEmail(),
                principal.getRole(),
                null,
                "Principal Administration",
                null,
                null
        );

        return new AuthResponseDto(accessToken, rawRefreshToken, jwtTokenProvider.getAccessExpirationSeconds(), userSummary);
    }

    public AuthResponseDto loginAdmin(LoginRequestDto req) {
        return loginPrincipal(req);
    }

    public PrincipalSetupStatusDto getPrincipalSetupStatus() {
        boolean activeExists = userRepository.existsByRoleAndStatus(Role.PRINCIPAL, UserStatus.ACTIVE);
        return new PrincipalSetupStatusDto(!activeExists);
    }

    public java.util.Map<String, Object> registerPrincipal(PrincipalRegisterRequestDto req, String clientIp) {
        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "UNKNOWN";
        if (!authRateLimiter.isAllowed("principal_reg_" + ip, 5, 900)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many registration attempts. Please try again in 15 minutes.");
        }

        // Enforce optional SETUP_TOKEN protection
        if (setupToken != null && !setupToken.trim().isEmpty()) {
            if (req.getSetupToken() == null || !setupToken.trim().equals(req.getSetupToken().trim())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Invalid setup token. Setup token is required to initialize the Principal account.");
            }
        }

        // Check if an active Principal already exists
        Optional<User> existingPrincipalOpt = userRepository.findFirstByRole(Role.PRINCIPAL);
        if (existingPrincipalOpt.isPresent()) {
            User existing = existingPrincipalOpt.get();
            if (existing.getStatus() == UserStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A Principal account already exists. Self-registration is strictly disabled.");
            }

            // Unverified pending registrations expire after 15 minutes and must not block a fresh attempt
            if (existing.getStatus() == UserStatus.PENDING_APPROVAL || existing.getStatus() == UserStatus.PENDING) {
                if (existing.getCreatedAt() != null && existing.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(15))) {
                    passwordResetTokenRepository.deleteByUser(existing);
                    userRepository.delete(existing);
                    userRepository.flush();
                } else if (!existing.getEmail().equalsIgnoreCase(req.getEmail().trim())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A Principal registration is currently pending verification for: " + existing.getEmail()
                                    + ". Please verify the OTP or wait 15 minutes for it to expire.");
                }
            }
        }

        String email = req.getEmail().trim().toLowerCase();
        Optional<User> existingUserWithEmail = userRepository.findByEmailIgnoreCase(email);
        if (existingUserWithEmail.isPresent() && existingUserWithEmail.get().getRole() != Role.PRINCIPAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address is already in use by another user.");
        }

        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }

        validatePrincipalPassword(req.getPassword(), email, req.getName());

        User principalUser;
        Optional<User> pendingOpt = userRepository.findFirstByRole(Role.PRINCIPAL);
        if (pendingOpt.isPresent() && pendingOpt.get().getEmail().equalsIgnoreCase(email)) {
            principalUser = pendingOpt.get();
            principalUser.setName(req.getName().trim());
            principalUser.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            principalUser.setCreatedAt(LocalDateTime.now());
            principalUser.setStatus(UserStatus.PENDING_APPROVAL);
        } else {
            principalUser = new User("PRIN-001", Role.PRINCIPAL, req.getName().trim(), email, passwordEncoder.encode(req.getPassword()), null);
            principalUser.setStatus(UserStatus.PENDING_APPROVAL);
            principalUser.setMustChangePassword(false);
            principalUser.setFailedAttempts(0);
        }

        try {
            principalUser = userRepository.saveAndFlush(principalUser);
        } catch (Exception ex) {
            logger.error("DB singleton guard prevented concurrent duplicate Principal: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A Principal account already exists. Self-registration is strictly disabled.");
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        String tokenHash = DigestUtils.sha256Hex(otp);
        passwordResetTokenRepository.invalidateAllForUser(principalUser);
        PasswordResetToken otpToken = new PasswordResetToken(principalUser, tokenHash, LocalDateTime.now().plusMinutes(15));
        passwordResetTokenRepository.save(otpToken);

        emailService.sendPrincipalRegistrationOtp(principalUser.getEmail(), otp);

        auditChainService.recordEntry(
                principalUser.getId(),
                principalUser.getName(),
                "PRINCIPAL_REGISTRATION_INITIATED",
                "USER",
                principalUser.getId() != null ? principalUser.getId().toString() : "PENDING",
                "{}",
                "{\"email\":\"" + principalUser.getEmail() + "\",\"status\":\"PENDING_APPROVAL\"}",
                "Principal self-registration initiated; OTP dispatched"
        );

        return java.util.Map.of(
                "message", "Registration initiated. A 6-digit OTP has been sent to " + principalUser.getEmail() + ". Please verify within 15 minutes.",
                "email", principalUser.getEmail(),
                "pendingVerification", true
        );
    }

    public AuthResponseDto verifyPrincipalOtp(com.intellicampus.auth.dto.AdminOtpVerifyDto req) {
        return verifyPrincipalOtp(req, "UNKNOWN");
    }

    public AuthResponseDto verifyPrincipalOtp(com.intellicampus.auth.dto.AdminOtpVerifyDto req, String clientIp) {
        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "UNKNOWN";
        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        String enteredOtp = req.getOtp() != null ? req.getOtp().trim() : "";

        String rateLimitKey = (!"UNKNOWN".equals(ip)) ? ("principal_otp_" + ip + "_" + email) : ("principal_otp_" + email);
        if (!authRateLimiter.isAllowed(rateLimitKey, 5, 900)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many OTP attempts. Please try again in 15 minutes.");
        }

        User principal = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired OTP."));

        if (principal.getRole() != Role.PRINCIPAL) {
            throw new BadCredentialsException("Invalid or expired OTP.");
        }

        // Expired pending registration check (> 15 min)
        if (principal.getStatus() == UserStatus.PENDING_APPROVAL || principal.getStatus() == UserStatus.PENDING) {
            if (principal.getCreatedAt() != null && principal.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(15))) {
                throw new BadCredentialsException("Registration OTP has expired (15 minutes). Please register again.");
            }
        }

        PasswordResetToken otpToken = passwordResetTokenRepository
                .findFirstByUserAndUsedFalseOrderByCreatedAtDesc(principal)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired OTP."));

        if (otpToken.isExpired() || otpToken.getAttempts() >= 5) {
            otpToken.setUsed(true);
            passwordResetTokenRepository.save(otpToken);
            throw new BadCredentialsException("OTP has expired. Please log in or register again.");
        }

        String enteredHash = DigestUtils.sha256Hex(enteredOtp);
        if (!otpToken.getTokenHash().equals(enteredHash)) {
            otpToken.incrementAttempts();
            passwordResetTokenRepository.save(otpToken);
            throw new BadCredentialsException("Invalid OTP.");
        }

        otpToken.setUsed(true);
        passwordResetTokenRepository.save(otpToken);

        principal.setFailedAttempts(0);
        principal.setLockedUntil(null);
        boolean wasPending = (principal.getStatus() == UserStatus.PENDING_APPROVAL || principal.getStatus() == UserStatus.PENDING);
        if (wasPending) {
            principal.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(principal);

        String accessToken = jwtTokenProvider.generateAccessToken(principal, null);
        String rawRefreshToken = generateSecureRandomToken();
        String hashedRefreshToken = DigestUtils.sha256Hex(rawRefreshToken);
        refreshTokenRepository.revokeAllForUser(principal);
        LocalDateTime expDate = LocalDateTime.now().plusDays(7);
        refreshTokenRepository.save(new RefreshToken(principal, hashedRefreshToken, expDate));

        if (wasPending) {
            auditChainService.recordEntry(
                    principal.getId(),
                    principal.getName(),
                    "PRINCIPAL_REGISTRATION_ACTIVATED",
                    "USER",
                    principal.getId().toString(),
                    "{}",
                    "{\"email\":\"" + principal.getEmail() + "\",\"role\":\"PRINCIPAL\",\"status\":\"ACTIVE\"}",
                    "Principal account verified and activated successfully"
            );
        } else {
            auditChainService.recordEntry(
                    principal.getId(),
                    principal.getName(),
                    "PRINCIPAL_LOGIN_SUCCESS",
                    "USER",
                    principal.getId().toString(),
                    "{}",
                    "{\"email\":\"" + principal.getEmail() + "\",\"role\":\"PRINCIPAL\",\"2fa\":true}",
                    "Principal successfully logged in via 2FA OTP"
            );
        }

        UserSummaryDto userSummary = new UserSummaryDto(
                principal.getId(),
                principal.getUserCode(),
                principal.getName(),
                principal.getEmail(),
                principal.getRole(),
                null,
                "Principal Administration",
                null,
                null
        );

        return new AuthResponseDto(accessToken, rawRefreshToken, jwtTokenProvider.getAccessExpirationSeconds(), userSummary);
    }

    public AuthResponseDto verifyAdminOtp(com.intellicampus.auth.dto.AdminOtpVerifyDto req) {
        return verifyPrincipalOtp(req);
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

    private void validatePrincipalPassword(String password, String email, String name) {
        validateStrongPasswordWithMinLength(password, email, name, 8);
    }

    private void validateStrongPassword(String password) {
        validateStrongPassword(password, null, null);
    }

    private void validateStrongPassword(String password, String email, String name) {
        validateStrongPasswordWithMinLength(password, email, name, 10);
    }

    private void validateStrongPasswordWithMinLength(String password, String email, String name, int minLength) {
        if (password == null || password.length() < minLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least " + minLength + " characters long.");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{}|;':\",./<>?`~".indexOf(ch) >= 0);

        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must include uppercase, lowercase, number, and special character.");
        }

        if (email != null && !email.isBlank()) {
            String emailPrefix = email.split("@")[0].toLowerCase();
            if (password.equalsIgnoreCase(email) || password.toLowerCase().contains(emailPrefix)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Password cannot be equal to or contain your email address.");
            }
        }

        if (name != null && !name.isBlank() && name.trim().length() >= 3) {
            String cleanName = name.trim().toLowerCase();
            if (password.toLowerCase().contains(cleanName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Password cannot contain your name.");
            }
        }
    }

    public void changePassword(User currentUser, ChangePasswordRequestDto req) {
        if (!passwordEncoder.matches(req.getCurrentPassword(), currentUser.getPasswordHash())) {
            throw new BadCredentialsException("Current password does not match.");
        }

        validateStrongPassword(req.getNewPassword(), currentUser.getEmail(), currentUser.getName());

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

        if (staff.getRole() == Role.PRINCIPAL) {
            throw new SecurityException("Modifying or approving the Principal is prohibited.");
        }

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
                "Staff approved by Principal " + adminUser.getName()
        );
    }

    public void rejectStaff(Long staffUserId, String reason, User adminUser) {
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found: " + staffUserId));

        if (staff.getRole() == Role.PRINCIPAL) {
            throw new SecurityException("The Principal account cannot be rejected or modified.");
        }

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
