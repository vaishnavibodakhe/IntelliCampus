package com.intellicampus.auth.service;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.auth.dto.*;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service("legacyAuthService")
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final DivisionRepository divisionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       StudentProfileRepository studentProfileRepository,
                       DepartmentRepository departmentRepository,
                       DivisionRepository divisionRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.departmentRepository = departmentRepository;
        this.divisionRepository = divisionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthResponseDto registerStudent(RegisterRequestDto req) {
        String email = req.getEmail().trim().toLowerCase();
        String rollNo = req.getRollNo().trim().toUpperCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered: " + email);
        }
        if (studentProfileRepository.existsByRollNo(rollNo)) {
            throw new IllegalArgumentException("Roll number is already registered: " + rollNo);
        }

        // Resolve or create department
        Department department = departmentRepository.findByName(req.getDepartment().trim())
                .orElseGet(() -> departmentRepository.save(new Department(
                        req.getDepartment().trim().toUpperCase().replaceAll("[^A-Z0-9]", "_"),
                        req.getDepartment().trim()
                )));

        // Resolve or create division for academic year
        String divName = "Year " + req.getYear() + " Div A";
        Division division = divisionRepository.findByNameAndDepartmentId(divName, department.getId())
                .orElseGet(() -> divisionRepository.save(new Division(department, req.getYear(), divName)));

        // Generate unique user code
        String baseCode = "STU-" + rollNo.replaceAll("[^A-Z0-9]", "-");
        String userCode = baseCode;
        int suffix = 1;
        while (userRepository.existsByUserCode(userCode)) {
            userCode = baseCode + "-" + suffix++;
        }

        // Hash password with BCrypt
        String passwordHash = passwordEncoder.encode(req.getPassword());

        User user = new User(userCode, Role.STUDENT, req.getName().trim(), email, passwordHash, department);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        User savedUser = userRepository.save(user);

        StudentProfile profile = new StudentProfile(savedUser, rollNo, division, null);
        studentProfileRepository.save(profile);

        // Issue JWT tokens
        String accessToken = jwtTokenProvider.generateAccessToken(savedUser, rollNo);
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser);

        UserSummaryDto userSummary = new UserSummaryDto(
                savedUser.getId(),
                savedUser.getUserCode(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                rollNo,
                department.getName(),
                req.getYear(),
                division.getName()
        );

        return new AuthResponseDto(accessToken, refreshToken, jwtTokenProvider.getAccessExpirationSeconds(), userSummary);
    }

    public AuthResponseDto login(LoginRequestDto req) {
        String email = req.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check if account is temporarily locked
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

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new DisabledException("Account is inactive. Please contact university administration.");
        }

        // Verify BCrypt password
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Reset failed login attempts on success
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Fetch student profile details if student
        String rollNo = null;
        Integer year = null;
        String divisionName = null;
        if (user.getRole() == Role.STUDENT) {
            Optional<StudentProfile> profileOpt = studentProfileRepository.findByUser(user);
            if (profileOpt.isPresent()) {
                StudentProfile profile = profileOpt.get();
                rollNo = profile.getRollNo();
                if (profile.getDivision() != null) {
                    divisionName = profile.getDivision().getName();
                    year = profile.getDivision().getYear();
                }
            }
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user, rollNo);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getUserCode(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                rollNo,
                user.getDepartment() != null ? user.getDepartment().getName() : "General",
                year,
                divisionName
        );

        return new AuthResponseDto(accessToken, refreshToken, jwtTokenProvider.getAccessExpirationSeconds(), userSummary);
    }
}
