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

    public UserService(UserRepository userRepository,
                       StudentProfileRepository studentProfileRepository,
                       DepartmentRepository departmentRepository,
                       DivisionRepository divisionRepository,
                       CsvUserValidator csvUserValidator) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.departmentRepository = departmentRepository;
        this.divisionRepository = divisionRepository;
        this.csvUserValidator = csvUserValidator;
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

        String rawPw = req.getRawPassword() != null ? req.getRawPassword() : "Welcome2026!";
        // Mock hash prefix for demonstrative simplicity or BCrypt standard format
        String pwHash = "$2a$10$e8f92a105c3b4e78912345678abcdef0123456789abcdef012345678";

        User user = new User(req.getUserCode(), req.getRole(), req.getName(), req.getEmail(), pwHash, dept);
        User savedUser = userRepository.save(user);

        if (req.getRole() == Role.STUDENT && req.getRollNo() != null) {
            Division div = null;
            final Department targetDept = dept;
            if (targetDept != null && req.getDivision() != null) {
                div = divisionRepository.findByNameAndDepartmentId(req.getDivision(), dept.getId())
                        .orElseGet(() -> divisionRepository.save(new Division(targetDept, 3, req.getDivision())));
            }
            StudentProfile profile = new StudentProfile(savedUser, req.getRollNo(), div, null);
            studentProfileRepository.save(profile);
        }

        return mapToDto(savedUser);
    }

    public CsvImportResultDto importCsv(List<String> csvLines) {
        CsvUserValidator.CsvValidationResult validation = csvUserValidator.validateCsvLines(csvLines);
        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>(validation.getErrors());

        for (CreateUserRequestDto dto : validation.getValidRows()) {
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

