package com.intellicampus.user.service;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.user.dto.CreateUserRequestDto;
import com.intellicampus.user.dto.CsvImportResultDto;
import com.intellicampus.user.dto.UserResponseDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.validation.CsvUserValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final DivisionRepository divisionRepository;
    private final CsvUserValidator csvUserValidator;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       StudentProfileRepository studentProfileRepository,
                       DepartmentRepository departmentRepository,
                       DivisionRepository divisionRepository,
                       CsvUserValidator csvUserValidator,
                       @org.springframework.beans.factory.annotation.Autowired(required = false)
                       org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.departmentRepository = departmentRepository;
        this.divisionRepository = divisionRepository;
        this.csvUserValidator = csvUserValidator;
        this.passwordEncoder = passwordEncoder != null ? passwordEncoder : new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers(String search, Role role) {
        List<User> users = userRepository.findAll();
        return users.stream()
            .filter(u -> role == null || u.getRole() == role)
            .filter(u -> {
                if (search == null || search.trim().isEmpty()) return true;
                String q = search.toLowerCase();
                return u.getName().toLowerCase().contains(q) ||
                       u.getUserCode().toLowerCase().contains(q) ||
                       u.getEmail().toLowerCase().contains(q);
            })
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<UserResponseDto> getUserById(Long id) {
        return userRepository.findById(id).map(this::mapToDto);
    }

    public UserResponseDto createUser(CreateUserRequestDto req) {
        if (req.getRole() == Role.PRINCIPAL) {
            throw new IllegalArgumentException("Registration or creation of PRINCIPAL accounts via API is strictly prohibited.");
        }
        if (userRepository.existsByUserCode(req.getUserCode())) {
            throw new IllegalArgumentException("User code already in use: " + req.getUserCode());
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());
        }

        Department dept = null;
        if (req.getDepartment() != null) {
            dept = departmentRepository.findByName(req.getDepartment())
                    .orElseGet(() -> departmentRepository.save(new Department(req.getDepartment().toUpperCase().replace(" ", "_"), req.getDepartment())));
        }

        String rawPw = req.getRawPassword() != null && !req.getRawPassword().trim().isEmpty() ? req.getRawPassword() : "Welcome2026!";
        String pwHash = passwordEncoder.encode(rawPw);

        User user = new User(req.getUserCode(), req.getRole(), req.getName(), req.getEmail(), pwHash, dept);
        user.setStatus(com.intellicampus.user.model.UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        User savedUser = userRepository.save(user);

        return mapToDto(savedUser);
    }

    public CsvImportResultDto importCsv(List<String> csvLines) {
        CsvUserValidator.CsvValidationResult validation = csvUserValidator.validateCsvLines(csvLines);
        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>(validation.getErrors());

        for (CreateUserRequestDto dto : validation.getValidRows()) {
            if (dto.getRole() == Role.PRINCIPAL) {
                failed++;
                errors.add("User '" + dto.getUserCode() + "': Principal cannot be imported. Role PRINCIPAL is prohibited in bulk CSV import.");
                continue;
            }
            try {
                createUser(dto);
                imported++;
            } catch (Exception ex) {
                failed++;
                errors.add("User '" + dto.getUserCode() + "': " + ex.getMessage());
            }
        }
        failed += validation.getErrors().size();

        int totalCount = csvLines != null ? csvLines.size() : 0;
        return new CsvImportResultDto(totalCount, imported, failed, errors);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        if (user.getRole() == Role.STUDENT) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Principals cannot modify or delete student accounts. Students manage their own registration and profile.");
        }
        if (user.getRole() == Role.PRINCIPAL) {
            throw new SecurityException("The Principal account cannot be deleted.");
        }
        userRepository.delete(user);
    }

    public void updateUserStatus(Long id, com.intellicampus.user.model.UserStatus status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        if (user.getRole() == Role.STUDENT) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Principals cannot alter student status. Students manage their own profile.");
        }
        if (user.getRole() == Role.PRINCIPAL && status != com.intellicampus.user.model.UserStatus.ACTIVE) {
            throw new SecurityException("The Principal account cannot be deactivated or disabled.");
        }
        user.setStatus(status);
        userRepository.save(user);
    }

    public void updateUserRole(Long id, Role newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        if (user.getRole() == Role.STUDENT) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Principals cannot change student roles.");
        }
        if (user.getRole() == Role.PRINCIPAL && newRole != Role.PRINCIPAL) {
            throw new SecurityException("The Principal role cannot be demoted or changed.");
        }
        if (newRole == Role.PRINCIPAL && user.getRole() != Role.PRINCIPAL) {
            throw new SecurityException("Promoting an account to PRINCIPAL is strictly prohibited.");
        }
        user.setRole(newRole);
        userRepository.save(user);
    }

    public void resetUserPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        if (user.getRole() == Role.STUDENT) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Principals cannot reset student passwords.");
        }
        if (user.getRole() == Role.PRINCIPAL) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Cannot reset PRINCIPAL password via this endpoint.");
        }
        String pw = (newPassword != null && !newPassword.isBlank()) ? newPassword.trim() : "Welcome2026!";
        user.setPasswordHash(passwordEncoder.encode(pw));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private UserResponseDto mapToDto(User u) {
        UserResponseDto dto = new UserResponseDto(
            u.getId(),
            u.getUserCode(),
            u.getName(),
            u.getEmail(),
            u.getRole(),
            u.getDepartment() != null ? u.getDepartment().getName() : "General",
            u.getStatus()
        );

        if (u.getRole() == Role.STUDENT) {
            studentProfileRepository.findByUser(u).ifPresent(p -> {
                dto.setRollNo(p.getRollNo());
                dto.setDivision(p.getDivision() != null ? p.getDivision().getName() : null);
                dto.setAttendanceRate(p.getAttendanceRate());
                dto.setFeeStatus(p.getFeeStatus());
                dto.setFaceEnrolled(Boolean.TRUE.equals(p.getFaceEnrolled()));
            });
        }
        return dto;
    }
}

