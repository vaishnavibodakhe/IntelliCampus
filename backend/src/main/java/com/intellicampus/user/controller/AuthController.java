package com.intellicampus.user.controller;

import com.intellicampus.user.dto.*;
import com.intellicampus.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Faculty Registration
     */
    @PostMapping("/faculty/register")
    public ResponseEntity<AuthResponse> registerFaculty(@Valid @RequestBody FacultyRegisterRequest request) {
        AuthResponse response = authService.registerFaculty(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Mentor Registration
     */
    @PostMapping("/mentor/register")
    public ResponseEntity<AuthResponse> registerMentor(@Valid @RequestBody MentorRegisterRequest request) {
        AuthResponse response = authService.registerMentor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Faculty Login
     */
    @PostMapping("/faculty/login")
    public ResponseEntity<AuthResponse> loginFaculty(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginFaculty(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Mentor Login
     */
    @PostMapping("/mentor/login")
    public ResponseEntity<AuthResponse> loginMentor(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.loginMentor(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout: blacklists token so it can no longer be used
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(new ApiResponse(true, "Successfully logged out. Token has been invalidated."));
    }

    /**
     * Request Password Reset Link
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ApiResponse response = authService.requestForgotPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate Reset Token (returns 200 if valid, 400 if expired or used)
     */
    @GetMapping("/validate-reset-token")
    public ResponseEntity<ApiResponse> validateResetToken(@RequestParam String token) {
        ApiResponse response = authService.validateResetToken(token);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset Password using Token
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ApiResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }
}
