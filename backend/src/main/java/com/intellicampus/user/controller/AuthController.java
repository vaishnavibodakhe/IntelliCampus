package com.intellicampus.user.controller;

import com.intellicampus.user.dto.*;
import com.intellicampus.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Faculty Registration (supports /api/auth/faculty/register and alias /api/faculty/register)
     */
    @PostMapping({"/api/auth/faculty/register", "/api/faculty/register"})
    public ResponseEntity<AuthResponse> registerFaculty(@Valid @RequestBody FacultyRegisterRequest request) {
        AuthResponse response = authService.registerFaculty(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Mentor Registration (supports /api/auth/mentor/register and alias /api/mentor/register)
     */
    @PostMapping({"/api/auth/mentor/register", "/api/mentor/register"})
    public ResponseEntity<AuthResponse> registerMentor(@Valid @RequestBody MentorRegisterRequest request) {
        AuthResponse response = authService.registerMentor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Faculty Login (supports /api/auth/faculty/login and alias /api/faculty/login)
     */
    @PostMapping({"/api/auth/faculty/login", "/api/faculty/login"})
    public ResponseEntity<AuthResponse> loginFaculty(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginFaculty(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Mentor Login (supports /api/auth/mentor/login and alias /api/mentor/login)
     */
    @PostMapping({"/api/auth/mentor/login", "/api/mentor/login"})
    public ResponseEntity<AuthResponse> loginMentor(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginMentor(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout: blacklists token so it can no longer be used (supports /api/auth/logout and alias /api/logout)
     */
    @PostMapping({"/api/auth/logout", "/api/logout"})
    public ResponseEntity<ApiResponse> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(new ApiResponse(true, "Successfully logged out. Token has been invalidated."));
    }

    /**
     * Request Password Reset OTP for Faculty (supports /api/faculty/forgot-password, /api/auth/faculty/forgot-password)
     */
    @PostMapping({"/api/faculty/forgot-password", "/api/auth/faculty/forgot-password"})
    public ResponseEntity<ApiResponse> forgotPasswordFaculty(@Valid @RequestBody ForgotPasswordRequest request) {
        ApiResponse response = authService.requestFacultyForgotPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify Faculty OTP (supports /api/faculty/verify-otp and alias /api/auth/faculty/verify-otp)
     */
    @PostMapping({"/api/faculty/verify-otp", "/api/auth/faculty/verify-otp"})
    public ResponseEntity<ApiResponse> verifyFacultyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        ApiResponse response = authService.verifyFacultyOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset Faculty Password with OTP verification token (supports /api/faculty/reset-password and alias /api/auth/faculty/reset-password)
     */
    @PostMapping({"/api/faculty/reset-password", "/api/auth/faculty/reset-password"})
    public ResponseEntity<ApiResponse> resetPasswordFaculty(@Valid @RequestBody ResetPasswordRequest request) {
        ApiResponse response = authService.resetFacultyPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * General / Legacy Password Reset (supports /api/auth/forgot-password)
     */
    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ApiResponse response = authService.requestForgotPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Request Password Reset OTP for Mentor (supports /api/mentor/forgot-password and alias /api/auth/mentor/forgot-password)
     */
    @PostMapping({"/api/mentor/forgot-password", "/api/auth/mentor/forgot-password"})
    public ResponseEntity<ApiResponse> forgotPasswordMentor(@Valid @RequestBody ForgotPasswordRequest request) {
        ApiResponse response = authService.requestMentorForgotPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify Mentor OTP (supports /api/mentor/verify-otp and alias /api/auth/mentor/verify-otp)
     */
    @PostMapping({"/api/mentor/verify-otp", "/api/auth/mentor/verify-otp"})
    public ResponseEntity<ApiResponse> verifyMentorOtp(@Valid @RequestBody VerifyOtpRequest request) {
        ApiResponse response = authService.verifyMentorOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset Mentor Password with OTP verification token (supports /api/mentor/reset-password and alias /api/auth/mentor/reset-password)
     */
    @PostMapping({"/api/mentor/reset-password", "/api/auth/mentor/reset-password"})
    public ResponseEntity<ApiResponse> resetPasswordMentor(@Valid @RequestBody ResetPasswordRequest request) {
        ApiResponse response = authService.resetMentorPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate Reset Token via query param (returns 200 if valid, 400 if expired or used)
     */
    @GetMapping("/api/auth/validate-reset-token")
    public ResponseEntity<ApiResponse> validateResetToken(@RequestParam String token) {
        ApiResponse response = authService.validateResetToken(token);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate Reset Token via path variable (supports /api/faculty/reset-password/{token} and /api/mentor/reset-password/{token})
     */
    @GetMapping({"/api/faculty/reset-password/{token}", "/api/mentor/reset-password/{token}"})
    public ResponseEntity<ApiResponse> validateResetTokenPath(@PathVariable("token") String token) {
        ApiResponse response = authService.validateResetToken(token);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset Password using Token in JSON body
     */
    @PostMapping("/api/auth/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ApiResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset Password using Token in Path (supports /api/faculty/reset-password/{token} and /api/mentor/reset-password/{token})
     */
    @PostMapping({"/api/faculty/reset-password/{token}", "/api/mentor/reset-password/{token}"})
    public ResponseEntity<ApiResponse> resetPasswordPath(
            @PathVariable("token") String token,
            @RequestBody ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().isBlank()) {
            request.setToken(token);
        }
        ApiResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }
}
