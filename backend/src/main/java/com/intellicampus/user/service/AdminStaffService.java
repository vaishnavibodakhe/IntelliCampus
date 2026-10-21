package com.intellicampus.user.service;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.auth.repository.RefreshTokenRepository;
import com.intellicampus.auth.service.EmailService;
import com.intellicampus.user.dto.*;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminStaffService {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%&*+-_=";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIAL;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditChainService auditChainService;
    private final EmailService emailService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AdminStaffService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            AuditChainService auditChainService,
            @Autowired(required = false) EmailService emailService,
            @Autowired(required = false) RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditChainService = auditChainService;
        this.emailService = emailService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public AdminStaffResponseDto createStaff(AdminStaffCreateDto req, User adminUser) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff request cannot be null.");
        }

        Role role = req.getRole();
        if (role != Role.HOD && role != Role.FACULTY && role != Role.MENTOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be HOD, FACULTY, or MENTOR.");
        }

        String email = req.getEmail() != null ? req.getEmail().trim().toLowerCase() : "";
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered: " + email);
        }

        String employeeCode = req.getEmployeeCode() != null ? req.getEmployeeCode().trim() : "";
        if (employeeCode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee code is required.");
        }
        if (userRepository.existsByUserCode(employeeCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee code is already registered: " + employeeCode);
        }

        Department department = resolveDepartment(req.getDepartment());

        // Enforce one HOD per department
        if (role == Role.HOD) {
            List<User> existingHods = userRepository.findByDepartmentAndRoleAndStatus(department, Role.HOD, UserStatus.ACTIVE);
            if (!existingHods.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Department '" + department.getName() + "' already has an active HOD (" + existingHods.get(0).getName() + ").");
            }
        }

        String tempPassword = generateSecureTemporaryPassword(16);
        String passwordHash = passwordEncoder.encode(tempPassword);

        User staff = new User();
        staff.setUserCode(employeeCode);
        staff.setName(req.getName().trim());
        staff.setEmail(email);
        staff.setRole(role);
        staff.setDepartment(department);
        staff.setPasswordHash(passwordHash);
        staff.setStatus(UserStatus.ACTIVE);
        staff.setMustChangePassword(true);
        staff.setFailedAttempts(0);

        User saved = userRepository.save(staff);

        boolean emailDispatched = false;
        if (emailService != null && emailService.isMailConfigured()) {
            try {
                emailService.sendStaffWelcomeEmail(saved.getEmail(), saved.getName(), tempPassword);
                emailDispatched = true;
            } catch (Exception ex) {
                // Email failure logged inside EmailService without logging password
            }
        }

        // Return temporary password ONCE if email was not dispatched/configured
        String returnPassword = emailDispatched ? null : tempPassword;

        // Record SHA-256 Audit Log
        if (auditChainService != null) {
            auditChainService.recordEntry(
                    adminUser != null ? adminUser.getId() : 1L,
                    adminUser != null ? adminUser.getName() : "System Admin",
                    "STAFF_CREATED",
                    "USER",
                    saved.getId().toString(),
                    "{}",
                    "{\"email\":\"" + saved.getEmail() + "\",\"role\":\"" + saved.getRole()
                            + "\",\"employeeCode\":\"" + saved.getUserCode()
                            + "\",\"department\":\"" + department.getName() + "\"}",
                    "Staff account created by admin"
            );
        }

        return toDto(saved, returnPassword);
    }

    @Transactional(readOnly = true)
    public List<AdminStaffResponseDto> getAllStaff(Role role, String department, UserStatus status, String search) {
        List<User> staffList = userRepository.findByRoleIn(List.of(Role.HOD, Role.FACULTY, Role.MENTOR));

        return staffList.stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> status == null || u.getStatus() == status)
                .filter(u -> {
                    if (department == null || department.isBlank()) return true;
                    if (u.getDepartment() == null) return false;
                    return u.getDepartment().getName().equalsIgnoreCase(department.trim())
                            || u.getDepartment().getCode().equalsIgnoreCase(department.trim());
                })
                .filter(u -> {
                    if (search == null || search.isBlank()) return true;
                    String q = search.trim().toLowerCase();
                    boolean matchName = u.getName() != null && u.getName().toLowerCase().contains(q);
                    boolean matchEmail = u.getEmail() != null && u.getEmail().toLowerCase().contains(q);
                    boolean matchCode = u.getUserCode() != null && u.getUserCode().toLowerCase().contains(q);
                    return matchName || matchEmail || matchCode;
                })
                .map(u -> toDto(u, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminStaffResponseDto getStaffById(Long id) {
        User user = findStaffUserOrThrow(id);
        return toDto(user, null);
    }

    public AdminStaffResponseDto updateStaff(Long id, AdminStaffUpdateDto req, User adminUser) {
        User staff = findStaffUserOrThrow(id);

        if (staff.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot modify ADMIN account via staff API.");
        }

        String oldEmail = staff.getEmail();
        Role oldRole = staff.getRole();
        Department oldDept = staff.getDepartment();

        if (req.getRole() != null) {
            if (req.getRole() != Role.HOD && req.getRole() != Role.FACULTY && req.getRole() != Role.MENTOR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be HOD, FACULTY, or MENTOR.");
            }
            staff.setRole(req.getRole());
        }

        if (req.getName() != null && !req.getName().isBlank()) {
            staff.setName(req.getName().trim());
        }

        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String newEmail = req.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(oldEmail)) {
                if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered: " + newEmail);
                }
                staff.setEmail(newEmail);
            }
        }

        if (req.getEmployeeCode() != null && !req.getEmployeeCode().isBlank()) {
            String newCode = req.getEmployeeCode().trim();
            if (!newCode.equalsIgnoreCase(staff.getUserCode())) {
                if (userRepository.existsByUserCode(newCode)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee code is already registered: " + newCode);
                }
                staff.setUserCode(newCode);
            }
        }

        if (req.getDepartment() != null && !req.getDepartment().isBlank()) {
            Department newDept = resolveDepartment(req.getDepartment());
            staff.setDepartment(newDept);
        }

        // Validate one HOD per department if role is HOD
        if (staff.getRole() == Role.HOD && staff.getDepartment() != null && staff.getStatus() == UserStatus.ACTIVE) {
            List<User> existingHods = userRepository.findByDepartmentAndRoleAndStatus(staff.getDepartment(), Role.HOD, UserStatus.ACTIVE);
            for (User hod : existingHods) {
                if (!hod.getId().equals(staff.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Department '" + staff.getDepartment().getName() + "' already has an active HOD (" + hod.getName() + ").");
                }
            }
        }

        User updated = userRepository.save(staff);

        if (auditChainService != null) {
            auditChainService.recordEntry(
                    adminUser != null ? adminUser.getId() : 1L,
                    adminUser != null ? adminUser.getName() : "System Admin",
                    "STAFF_UPDATED",
                    "USER",
                    updated.getId().toString(),
                    "{\"email\":\"" + oldEmail + "\",\"role\":\"" + oldRole + "\"}",
                    "{\"email\":\"" + updated.getEmail() + "\",\"role\":\"" + updated.getRole() + "\"}",
                    "Staff account updated by admin"
            );
        }

        return toDto(updated, null);
    }

    public AdminStaffResponseDto updateStaffStatus(Long id, AdminStaffStatusDto req, User adminUser) {
        User staff = findStaffUserOrThrow(id);

        if (staff.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot alter ADMIN account status.");
        }

        UserStatus newStatus = req != null ? req.getStatus() : null;
        if (newStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid status is required.");
        }

        UserStatus oldStatus = staff.getStatus();
        staff.setStatus(newStatus);

        // If status becomes inactive/disabled, revoke active refresh tokens immediately
        if (newStatus != UserStatus.ACTIVE && refreshTokenRepository != null) {
            refreshTokenRepository.revokeAllForUser(staff);
        }

        // Check HOD uniqueness on reactivation
        if (staff.getRole() == Role.HOD && newStatus == UserStatus.ACTIVE && staff.getDepartment() != null) {
            List<User> existingHods = userRepository.findByDepartmentAndRoleAndStatus(staff.getDepartment(), Role.HOD, UserStatus.ACTIVE);
            for (User hod : existingHods) {
                if (!hod.getId().equals(staff.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Department '" + staff.getDepartment().getName() + "' already has an active HOD (" + hod.getName() + ").");
                }
            }
        }

        User updated = userRepository.save(staff);

        String action = (newStatus == UserStatus.ACTIVE) ? "STAFF_ACTIVATED" : "STAFF_DEACTIVATED";
        if (auditChainService != null) {
            auditChainService.recordEntry(
                    adminUser != null ? adminUser.getId() : 1L,
                    adminUser != null ? adminUser.getName() : "System Admin",
                    action,
                    "USER",
                    updated.getId().toString(),
                    "{\"status\":\"" + oldStatus + "\"}",
                    "{\"status\":\"" + newStatus + "\"}",
                    "Staff status changed by admin"
            );
        }

        return toDto(updated, null);
    }

    public AdminStaffResponseDto resetStaffPassword(Long id, User adminUser) {
        User staff = findStaffUserOrThrow(id);

        if (staff.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot reset ADMIN password via staff API.");
        }

        String tempPassword = generateSecureTemporaryPassword(16);
        String passwordHash = passwordEncoder.encode(tempPassword);

        staff.setPasswordHash(passwordHash);
        staff.setMustChangePassword(true);
        staff.setFailedAttempts(0);
        staff.setLockedUntil(null);

        if (refreshTokenRepository != null) {
            refreshTokenRepository.revokeAllForUser(staff);
        }

        User updated = userRepository.save(staff);

        boolean emailDispatched = false;
        if (emailService != null && emailService.isMailConfigured()) {
            try {
                emailService.sendStaffPasswordResetEmail(updated.getEmail(), updated.getName(), tempPassword);
                emailDispatched = true;
            } catch (Exception ex) {
                // Email failure logged inside EmailService without logging password
            }
        }

        String returnPassword = emailDispatched ? null : tempPassword;

        if (auditChainService != null) {
            auditChainService.recordEntry(
                    adminUser != null ? adminUser.getId() : 1L,
                    adminUser != null ? adminUser.getName() : "System Admin",
                    "PASSWORD_RESET_BY_ADMIN",
                    "USER",
                    updated.getId().toString(),
                    "{}",
                    "{\"email\":\"" + updated.getEmail() + "\",\"mustChangePassword\":true}",
                    "Temporary password generated by admin"
            );
        }

        return toDto(updated, returnPassword);
    }

    public void deleteStaff(Long id, User adminUser) {
        User staff = findStaffUserOrThrow(id);

        if (staff.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete ADMIN account.");
        }

        // Soft delete: set status to DISABLED and revoke tokens
        staff.setStatus(UserStatus.DISABLED);
        if (refreshTokenRepository != null) {
            refreshTokenRepository.revokeAllForUser(staff);
        }
        userRepository.save(staff);

        if (auditChainService != null) {
            auditChainService.recordEntry(
                    adminUser != null ? adminUser.getId() : 1L,
                    adminUser != null ? adminUser.getName() : "System Admin",
                    "STAFF_DELETED",
                    "USER",
                    staff.getId().toString(),
                    "{\"status\":\"ACTIVE\"}",
                    "{\"status\":\"DISABLED\"}",
                    "Staff account soft-deleted by admin"
            );
        }
    }

    private User findStaffUserOrThrow(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found with ID: " + id));

        if (user.getRole() != Role.HOD && user.getRole() != Role.FACULTY && user.getRole() != Role.MENTOR) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a staff member.");
        }
        return user;
    }

    private Department resolveDepartment(String departmentInput) {
        if (departmentInput == null || departmentInput.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is required.");
        }
        String input = departmentInput.trim();

        // 1. Try by numeric ID
        try {
            Long deptId = Long.parseLong(input);
            Optional<Department> byId = departmentRepository.findById(deptId);
            if (byId.isPresent()) return byId.get();
        } catch (NumberFormatException ignored) {}

        // 2. Try by exact name
        Optional<Department> byName = departmentRepository.findByName(input);
        if (byName.isPresent()) return byName.get();

        // 3. Try by code
        Optional<Department> byCode = departmentRepository.findByCode(input);
        if (byCode.isPresent()) return byCode.get();

        // 4. Try case-insensitive matching across all departments
        List<Department> all = departmentRepository.findAll();
        for (Department d : all) {
            if (d.getName().equalsIgnoreCase(input) || d.getCode().equalsIgnoreCase(input)) {
                return d;
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department not found: " + departmentInput);
    }

    private String generateSecureTemporaryPassword(int length) {
        List<Character> chars = new ArrayList<>();
        // Guarantee at least 2 of each required character category
        chars.add(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        chars.add(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        chars.add(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        chars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        chars.add(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
        chars.add(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));

        for (int i = chars.size(); i < length; i++) {
            chars.add(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
        }

        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    private AdminStaffResponseDto toDto(User u, String tempPassword) {
        return new AdminStaffResponseDto(
                u.getId(),
                u.getUserCode(),
                u.getName(),
                u.getEmail(),
                u.getRole(),
                u.getDepartment() != null ? u.getDepartment().getName() : null,
                u.getDepartment() != null ? u.getDepartment().getId() : null,
                u.getStatus(),
                u.getMustChangePassword(),
                tempPassword,
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
