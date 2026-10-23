package com.intellicampus.auth.controller;

import com.intellicampus.auth.dto.AuthResponseDto;
import com.intellicampus.auth.dto.LoginRequestDto;
import com.intellicampus.auth.service.StaffAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/auth/principal", "/api/auth/admin"})
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174", "http://localhost:3000"})
public class PrincipalAuthController {

    private final StaffAuthService staffAuthService;

    public PrincipalAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    /**
     * Public Setup Status Endpoint.
     * Returns { "setupRequired": true } if no active Principal exists in the database.
     * Leaks no other institutional or user information.
     */
    @GetMapping("/setup-status")
    public ResponseEntity<com.intellicampus.auth.dto.PrincipalSetupStatusDto> getSetupStatus() {
        return ResponseEntity.ok(staffAuthService.getPrincipalSetupStatus());
    }

    /**
     * First-run Principal Registration Endpoint.
     * Allowed ONLY when no Principal exists yet in the database.
     */
    @PostMapping("/register")
    public ResponseEntity<java.util.Map<String, Object>> register(
            @Valid @RequestBody com.intellicampus.auth.dto.PrincipalRegisterRequestDto req,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "UNKNOWN";
        java.util.Map<String, Object> result = staffAuthService.registerPrincipal(req, clientIp);
        return ResponseEntity.ok(result);
    }

    /**
     * Dedicated Principal Login Endpoint.
     * Strictly authenticates ONLY accounts with role PRINCIPAL.
     * All non-principal roles and invalid credentials receive the exact same generic 401 response.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto req) {
        AuthResponseDto response = staffAuthService.loginPrincipal(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponseDto> verifyOtp(@Valid @RequestBody com.intellicampus.auth.dto.AdminOtpVerifyDto req) {
        AuthResponseDto response = staffAuthService.verifyPrincipalOtp(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<java.util.Map<String, String>> changePassword(
            @Valid @RequestBody com.intellicampus.auth.dto.ChangePasswordRequestDto req,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.intellicampus.user.model.User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(java.util.Map.of("error", "Authentication required"));
        }
        staffAuthService.changePassword(currentUser, req);
        return ResponseEntity.ok(java.util.Map.of("message", "Password changed successfully"));
    }
}
