package com.intellicampus.auth.controller;

import com.intellicampus.auth.dto.AuthResponseDto;
import com.intellicampus.auth.dto.LoginRequestDto;
import com.intellicampus.auth.service.StaffAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/admin")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174", "http://localhost:3000"})
public class AdminAuthController {

    private final StaffAuthService staffAuthService;

    public AdminAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    /**
     * Dedicated Admin Login Endpoint.
     * Strictly authenticates ONLY accounts with role ADMIN.
     * All non-admin roles and invalid credentials receive the exact same generic 401 response.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto req) {
        AuthResponseDto response = staffAuthService.loginAdmin(req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponseDto> verifyOtp(@Valid @RequestBody com.intellicampus.auth.dto.AdminOtpVerifyDto req) {
        AuthResponseDto response = staffAuthService.verifyAdminOtp(req);
        return ResponseEntity.ok(response);
    }
}
