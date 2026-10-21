package com.intellicampus.user.service;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.user.dto.AuthResponseDto;
import com.intellicampus.user.dto.LoginRequestDto;
import com.intellicampus.user.dto.StudentRegisterRequestDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.TokenDenylist;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.TokenDenylistRepository;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.intellicampus.audit.service.AuditChainService;

@Service
@Transactional
public class StudentAuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern INDIAN_PHONE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$");

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final DivisionRepository divisionRepository;
    private final TokenDenylistRepository tokenDenylistRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final IpRateLimiterService ipRateLimiterService;
    private final AuditChainService auditChainService;

    @Value("${app.jwt.lockout-max-attempts:5}")
    private int lockoutMaxAttempts = 5;

    @Value("${app.jwt.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes = 15;

    public StudentAuthService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            DepartmentRepository departmentRepository,
            DivisionRepository divisionRepository,
            TokenDenylistRepository tokenDenylistRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this(userRepository, studentProfileRepository, departmentRepository, divisionRepository,
                tokenDenylistRepository, jwtService, passwordEncoder, null, null);
    }

    @Autowired
    public StudentAuthService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            DepartmentRepository departmentRepository,
            DivisionRepository divisionRepository,
            TokenDenylistRepository tokenDenylistRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            IpRateLimiterService ipRateLimiterService,
            AuditChainService auditChainService) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.departmentRepository = departmentRepository;
        this.divisionRepository = divisionRepository;
        this.tokenDenylistRepository = tokenDenylistRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.ipRateLimiterService = ipRateLimiterService;
        this.auditChainService = auditChainService;
    }

    public AuthResponseDto registerStudent(StudentRegisterRequestDto req) {
        validateRegistrationInput(req);

        // Normalize inputs
        String email = req.getEmail().trim().toLowerCase();
        String rollNo = req.getRollNo().trim().toUpperCase();
        String name = req.getFullName().trim();
        String contactNo = req.getContactNo().trim();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email address is already registered: " + email);
        }
        if (studentProfileRepository.findByRollNo(rollNo).isPresent()) {
            throw new IllegalArgumentException("Student Roll Number / PRN is already registered: " + rollNo);
        }

        // Find or create department
        String deptName = req.getDepartment() != null && !req.getDepartment().isBlank()
                ? req.getDepartment().trim()
                : "Computer Science";
        String rawCode = deptName.toUpperCase().replaceAll("[^A-Z0-9]", "_").replaceAll("_+", "_");
        String deptCode = rawCode.length() > 20 ? rawCode.substring(0, 20) : rawCode;
        Department dept = departmentRepository.findByName(deptName)
                .orElseGet(() -> departmentRepository.save(new Department(deptCode, deptName)));

        // Find or create division
        String divName = req.getDivision() != null && !req.getDivision().isBlank()
                ? req.getDivision().trim()
                : "Div A";
        int yearNum = 3;
        if (req.getYear() != null) {
            String digits = req.getYear().replaceAll("\\D+", "");
            if (!digits.isEmpty()) {
                yearNum = Integer.parseInt(digits);
            }
        }
        final int targetYear = yearNum;
        String canonicalName = "Year " + targetYear + " " + divName;
        Division division = divisionRepository.findByNameAndDepartmentId(canonicalName, dept.getId())
                .or(() -> divisionRepository.findByNameAndDepartmentId(divName, dept.getId()))
                .orElseGet(() -> divisionRepository.save(new Division(dept, targetYear, divName)));

        // Generate user code e.g. student.cs104
        String userCode = "student." + rollNo.toLowerCase().replace("-", "").replace("/", "");
        if (userRepository.existsByUserCode(userCode)) {
            userCode = userCode + "." + System.currentTimeMillis() % 1000;
        }

        // Hash password with BCrypt
        String passwordHash = passwordEncoder.encode(req.getPassword());

        User user = new User(userCode, Role.STUDENT, name, email, passwordHash, dept);
        user.setStatus(UserStatus.PENDING);
        User savedUser = userRepository.save(user);

        StudentProfile profile = new StudentProfile(savedUser, rollNo, division, null);
        profile.setContactNo(contactNo);
        profile.setConsentAt(LocalDateTime.now());
        profile.setConsentVersion(req.getConsentVersion() != null && !req.getConsentVersion().isBlank()
                ? req.getConsentVersion()
                : "1.0");
        StudentProfile savedProfile = studentProfileRepository.save(profile);

        // Record backend audit entry for registration
        if (auditChainService != null) {
            auditChainService.recordEntry(
                    savedUser.getId(),
                    savedUser.getName(),
                    "STUDENT_REGISTRATION",
                    "USER",
                    String.valueOf(savedUser.getId()),
                    "{}",
                    "{\"status\":\"PENDING\",\"rollNo\":\"" + savedProfile.getRollNo() + "\"}",
                    "Student registration initiated with DPDP Act 2023 biometric consent"
            );
        }

        // Generate JWT token
        String token = jwtService.generateToken(savedUser);

        return new AuthResponseDto(
                token,
                jwtService.getAccessExpirationMinutes(),
                "Student account registered successfully. Please complete biometric face enrollment.",
                mapUserToMap(savedUser),
                mapStudentToMap(savedProfile)
        );
    }

    public AuthResponseDto login(LoginRequestDto req) {
        return login(req, "127.0.0.1");
    }

    public AuthResponseDto login(LoginRequestDto req, String clientIp) {
        if (ipRateLimiterService != null && !ipRateLimiterService.isLoginIpAllowed(clientIp)) {
            throw new IllegalStateException("Too many failed login attempts from this IP address. Please try again after 10 minutes.");
        }

        if (req == null || req.getIdentifier() == null || req.getIdentifier().isBlank()) {
            throw new IllegalArgumentException("Email or Roll Number is required.");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }

        String identifier = req.getIdentifier().trim();
        User user = findUserByIdentifier(identifier).orElse(null);

        if (user == null) {
            if (ipRateLimiterService != null) {
                ipRateLimiterService.recordFailedLogin(clientIp);
            }
            throw new IllegalArgumentException("Invalid credentials. Please verify your email/roll number and password.");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new IllegalStateException("Account registration is pending biometric face enrollment. Please complete face enrollment before logging in.");
        }

        // Check account lockout
        if (user.getLockedUntil() != null) {
            if (LocalDateTime.now().isBefore(user.getLockedUntil())) {
                long minutesRemaining = ChronoUnit.MINUTES.between(LocalDateTime.now(), user.getLockedUntil()) + 1;
                throw new IllegalStateException(String.format("Account is temporarily locked due to %d consecutive failed login attempts. Please try again after %d minutes.", lockoutMaxAttempts, minutesRemaining));
            } else {
                // Lock has expired; reset
                user.setLockedUntil(null);
                user.setFailedAttempts(0);
                userRepository.save(user);
            }
        }

        // Validate password
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            if (ipRateLimiterService != null) {
                ipRateLimiterService.recordFailedLogin(clientIp);
            }
            int failed = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(failed);

            if (failed >= lockoutMaxAttempts) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
                userRepository.save(user);
                throw new IllegalStateException(String.format("Account locked due to %d consecutive failed login attempts. Please try again after %d minutes.", lockoutMaxAttempts, lockoutDurationMinutes));
            } else {
                userRepository.save(user);
                int remaining = lockoutMaxAttempts - failed;
                throw new IllegalArgumentException(String.format("Invalid credentials. %d attempt%s remaining before account lockout.", remaining, remaining == 1 ? "" : "s"));
            }
        }

        // Successful login: reset failed attempts and IP rate limiting
        if (ipRateLimiterService != null) {
            ipRateLimiterService.resetLoginIp(clientIp);
        }
        if (user.getFailedAttempts() != null && user.getFailedAttempts() > 0) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        // Record backend audit entry for login
        if (auditChainService != null) {
            auditChainService.recordEntry(
                    user.getId(),
                    user.getName(),
                    "STUDENT_LOGIN",
                    "USER",
                    String.valueOf(user.getId()),
                    "{}",
                    "{\"email\":\"" + user.getEmail() + "\",\"role\":\"" + user.getRole().name() + "\"}",
                    "Student " + user.getName() + " signed in successfully via JWT token"
            );
        }

        String token = jwtService.generateToken(user);
        StudentProfile studentProfile = null;
        if (user.getRole() == Role.STUDENT) {
            studentProfile = studentProfileRepository.findByUserId(user.getId()).orElse(null);
        }

        return new AuthResponseDto(
                token,
                jwtService.getAccessExpirationMinutes(),
                "Login successful. Welcome back, " + user.getName() + "!",
                mapUserToMap(user),
                studentProfile != null ? mapStudentToMap(studentProfile) : null
        );
    }

    public Map<String, Object> logout(String authHeader) {
        String token = extractTokenFromHeader(authHeader);
        User user = null;
        if (token != null && !token.isBlank()) {
            try {
                if (jwtService.isTokenValid(token)) {
                    LocalDateTime expiresAt = jwtService.extractExpirationAsLocalDateTime(token);
                    String email = jwtService.extractEmail(token);
                    user = userRepository.findByEmail(email).orElse(null);

                    if (!tokenDenylistRepository.existsByToken(token)) {
                        TokenDenylist denylistEntry = new TokenDenylist(token, user, expiresAt);
                        tokenDenylistRepository.save(denylistEntry);
                    }
                }
            } catch (Exception e) {
                // If token cannot be decoded, still proceed with logout
            }
        }

        if (auditChainService != null) {
            Long actorId = user != null ? user.getId() : 0L;
            String actorName = user != null ? user.getName() : "Unknown";
            String email = user != null ? user.getEmail() : "unknown";
            auditChainService.recordEntry(
                    actorId,
                    actorName,
                    "USER_LOGOUT",
                    "USER",
                    String.valueOf(actorId),
                    "{\"email\":\"" + email + "\"}",
                    "{\"session\":\"TERMINATED\"}",
                    "Student " + actorName + " logged out; session token revoked and denylisted"
            );
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("message", "User session logged out successfully. Token invalidated.");
        return resp;
    }

    public Map<String, Object> getCurrentUserProfile(String authHeader) {
        String token = extractTokenFromHeader(authHeader);
        if (token == null || !jwtService.isTokenValid(token)) {
            throw new SecurityException("Authentication token is missing, expired, or invalidated. Please login.");
        }

        String email = jwtService.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("User associated with token not found."));

        StudentProfile student = null;
        if (user.getRole() == Role.STUDENT) {
            student = studentProfileRepository.findByUserId(user.getId()).orElse(null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("user", mapUserToMap(user));
        result.put("student", student != null ? mapStudentToMap(student) : null);
        return result;
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        // 1. Try email
        Optional<User> byEmail = userRepository.findByEmail(identifier.toLowerCase());
        if (byEmail.isPresent()) return byEmail;

        // 2. Try student roll number
        Optional<StudentProfile> byRoll = studentProfileRepository.findByRollNo(identifier.toUpperCase());
        if (byRoll.isPresent() && byRoll.get().getUser() != null) {
            return Optional.of(byRoll.get().getUser());
        }

        // 3. Try user code
        return userRepository.findByUserCode(identifier);
    }

    private void validateRegistrationInput(StudentRegisterRequestDto req) {
        if (req == null) {
            throw new IllegalArgumentException("Registration payload is mandatory.");
        }
        if (req.getFullName() == null || req.getFullName().trim().length() < 2) {
            throw new IllegalArgumentException("Full Name is mandatory and must contain at least 2 characters.");
        }
        if (req.getEmail() == null || !EMAIL_PATTERN.matcher(req.getEmail().trim()).matches()) {
            throw new IllegalArgumentException("Valid college or personal email address is required (e.g., student@intellicampus.edu).");
        }
        if (req.getContactNo() == null || !INDIAN_PHONE_PATTERN.matcher(req.getContactNo().trim()).matches()) {
            throw new IllegalArgumentException("A valid 10-digit Indian mobile number starting with 6, 7, 8, or 9 is required.");
        }
        if (req.getRollNo() == null || req.getRollNo().trim().length() < 3) {
            throw new IllegalArgumentException("Roll Number / PRN is mandatory and must be at least 3 characters.");
        }
        if (req.getPassword() == null || !STRONG_PASSWORD_PATTERN.matcher(req.getPassword()).matches()) {
            throw new IllegalArgumentException("Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character.");
        }
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and Confirm Password do not match.");
        }
        if (req.getDepartment() == null || req.getDepartment().trim().isBlank()) {
            throw new IllegalArgumentException("Department selection is required.");
        }
        if (req.getDivision() == null || req.getDivision().trim().isBlank()) {
            throw new IllegalArgumentException("Division selection is required.");
        }
    }

    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader;
    }

    private Map<String, Object> mapUserToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("userCode", user.getUserCode());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole().name());
        map.put("department", user.getDepartment() != null ? user.getDepartment().getName() : "");
        map.put("status", user.getStatus().name());
        return map;
    }

    private Map<String, Object> mapStudentToMap(StudentProfile profile) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", profile.getId());
        map.put("rollNo", profile.getRollNo());
        map.put("contactNo", profile.getContactNo());
        map.put("division", profile.getDivision() != null ? profile.getDivision().getName() : "");
        map.put("year", profile.getDivision() != null ? "Year " + profile.getDivision().getYear() : "Year 3");
        map.put("faceEnrolled", profile.getFaceEnrolled());
        map.put("attendanceRate", profile.getAttendanceRate());
        map.put("feeStatus", profile.getFeeStatus());
        map.put("consentAt", profile.getConsentAt());
        map.put("consentVersion", profile.getConsentVersion());
        return map;
    }

    @Scheduled(cron = "${app.cleanup.pending-cron:0 0 * * * *}")
    public int cleanupPendingAccountsOlderThan24Hours() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        java.util.List<User> pendingUsers = userRepository.findByStatusAndCreatedAtBefore(UserStatus.PENDING, cutoff);
        int count = 0;
        for (User u : pendingUsers) {
            studentProfileRepository.findByUser(u).ifPresent(studentProfileRepository::delete);
            tokenDenylistRepository.deleteByUserId(u.getId());
            userRepository.delete(u);
            count++;
        }
        return count;
    }
}
