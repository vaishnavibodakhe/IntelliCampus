package com.intellicampus.user.controller;

import com.intellicampus.security.util.SecurityUtils;
import com.intellicampus.user.dto.*;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.service.AdminStaffService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/principal/staff", "/api/admin/staff"})
@PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
@CrossOrigin(origins = "*")
public class AdminStaffController {

    private final AdminStaffService adminStaffService;
    private final SecurityUtils securityUtils;

    public AdminStaffController(
            AdminStaffService adminStaffService,
            @Autowired(required = false) SecurityUtils securityUtils) {
        this.adminStaffService = adminStaffService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    public ResponseEntity<AdminStaffResponseDto> createStaff(@Valid @RequestBody AdminStaffCreateDto req) {
        User adminUser = getAuthenticatedAdmin();
        AdminStaffResponseDto created = adminStaffService.createStaff(req, adminUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<AdminStaffResponseDto>> getAllStaff(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search) {
        List<AdminStaffResponseDto> list = adminStaffService.getAllStaff(role, department, status, search);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminStaffResponseDto> getStaffById(@PathVariable Long id) {
        AdminStaffResponseDto staff = adminStaffService.getStaffById(id);
        return ResponseEntity.ok(staff);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminStaffResponseDto> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody AdminStaffUpdateDto req) {
        User adminUser = getAuthenticatedAdmin();
        AdminStaffResponseDto updated = adminStaffService.updateStaff(id, req, adminUser);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminStaffResponseDto> updateStaffStatus(
            @PathVariable Long id,
            @RequestBody AdminStaffStatusDto req) {
        User adminUser = getAuthenticatedAdmin();
        AdminStaffResponseDto updated = adminStaffService.updateStaffStatus(id, req, adminUser);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<AdminStaffResponseDto> resetStaffPassword(
            @PathVariable Long id,
            @RequestBody(required = false) AdminStaffResetPasswordDto req) {
        User adminUser = getAuthenticatedAdmin();
        String newPw = req != null ? req.getNewPassword() : null;
        AdminStaffResponseDto result = (newPw != null && !newPw.isBlank())
                ? adminStaffService.resetStaffPassword(id, newPw, adminUser)
                : adminStaffService.resetStaffPassword(id, adminUser);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteStaff(@PathVariable Long id) {
        User adminUser = getAuthenticatedAdmin();
        adminStaffService.deleteStaff(id, adminUser);
        return ResponseEntity.ok(Map.of("message", "Staff account soft-deleted successfully"));
    }

    private User getAuthenticatedAdmin() {
        if (securityUtils != null) {
            try {
                return securityUtils.getCurrentUser().orElse(null);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
