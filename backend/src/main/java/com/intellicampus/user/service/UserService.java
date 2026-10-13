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

    public UserService(UserRepository userRepository,
                       StudentProfileRepository studentProfileRepository,
                       DepartmentRepository departmentRepository,
                       DivisionRepository divisionRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.departmentRepository = departmentRepository;
        this.divisionRepository = divisionRepository;
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
            if (dept != null && req.getDivision() != null) {
                div = divisionRepository.findByNameAndDepartmentId(req.getDivision(), dept.getId())
                        .orElseGet(() -> divisionRepository.save(new Division(dept, 3, req.getDivision())));
            }
            StudentProfile profile = new StudentProfile(savedUser, req.getRollNo(), div, null);
            studentProfileRepository.save(profile);
        }

        return mapToDto(savedUser);
    }

    public CsvImportResultDto importCsv(List<String> csvLines) {
        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < csvLines.size(); i++) {
            String line = csvLines.get(i).trim();
            if (line.isEmpty() || line.startsWith("userCode") || line.startsWith("#")) continue;

            String[] parts = line.split(",");
            if (parts.length < 5) {
                failed++;
                errors.add("Line " + (i + 1) + ": Insufficient columns (expected userCode,name,email,role,department,rollNo)");
                continue;
            }

            try {
                CreateUserRequestDto dto = new CreateUserRequestDto();
                dto.setUserCode(parts[0].trim());
                dto.setName(parts[1].trim());
                dto.setEmail(parts[2].trim());
                dto.setRole(Role.valueOf(parts[3].trim().toUpperCase()));
                dto.setDepartment(parts[4].trim());
                if (parts.length > 5) dto.setRollNo(parts[5].trim());
                if (parts.length > 6) dto.setDivision(parts[6].trim());

                createUser(dto);
                imported++;
            } catch (Exception ex) {
                failed++;
                errors.add("Line " + (i + 1) + " (" + parts[0] + "): " + ex.getMessage());
            }
        }

        return new CsvImportResultDto(csvLines.size(), imported, failed, errors);
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
            });
        }
        return dto;
    }
}
