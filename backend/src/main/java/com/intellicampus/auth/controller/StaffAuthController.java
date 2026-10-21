package com.intellicampus.auth.controller;

import com.intellicampus.auth.dto.*;
import com.intellicampus.auth.service.StaffAuthService;
import com.intellicampus.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/auth/staff", "/api/auth/hod"})
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174", "http://localhost:3000"})
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    public StaffAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<PendingStaffApprovalDto> register(@Valid @RequestBody StaffRegisterRequestDto req, HttpServletRequest request) {
        if (req.getRole() == com.intellicampus.user.model.Role.ADMIN) {
            throw new IllegalArgumentException("Registration of ADMIN accounts is strictly prohibited.");
        }
        if (req.getRole() == null) {
            req.setRole(com.intellicampus.user.model.Role.HOD);
        }
        PendingStaffApprovalDto response = staffAuthService.registerStaff(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto req) {
        AuthResponseDto response = staffAuthService.loginStaff(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto req) {
        AuthResponseDto response = staffAuthService.refreshAccessToken(req.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshTokenRequestDto req,
            @AuthenticationPrincipal User currentUser) {
        String refreshToken = req != null ? req.getRefreshToken() : null;
        staffAuthService.logout(refreshToken, currentUser);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto req,
            HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        String uri = request != null ? request.getRequestURI() : "";
        com.intellicampus.user.model.Role role = req.getRole();
        if (role == null) {
            role = com.intellicampus.user.model.Role.HOD;
        }
        String message = staffAuthService.forgotPassword(req.getEmail(), role, clientIp);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping({"/reset-password", "/verify-otp"})
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto req) {
        staffAuthService.resetPasswordWithOtp(req);
        return ResponseEntity.ok(Map.of("message", "Password has been successfully reset. You may now log in."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDto req,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        staffAuthService.changePassword(currentUser, req);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        UserSummaryDto summary = staffAuthService.getCurrentStaff(currentUser);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PendingStaffApprovalDto>> getPendingApprovals() {
        List<PendingStaffApprovalDto> list = staffAuthService.getPendingApprovals();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> approveStaff(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal User adminUser) {
        staffAuthService.approveStaff(id, adminUser);
        return ResponseEntity.ok(Map.of("message", "Staff account approved successfully"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> rejectStaff(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User adminUser) {
        String reason = body != null ? body.get("reason") : "Rejected by Admin";
        staffAuthService.rejectStaff(id, reason, adminUser);
        return ResponseEntity.ok(Map.of("message", "Staff account rejected"));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
