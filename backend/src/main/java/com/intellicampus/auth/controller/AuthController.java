package com.intellicampus.auth.controller;

import com.intellicampus.auth.dto.*;
import com.intellicampus.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto loginRequest) {
        AuthResponseDto response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserSummaryDto> register(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("loginCode") String loginCode,
            @RequestParam("rollNo") String rollNo,
            @RequestParam("department") String department,
            @RequestParam(value = "year", required = false) String year,
            @RequestParam("division") String division,
            @RequestParam("password") String password,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
            @RequestParam(value = "biometricConsent", required = false, defaultValue = "false") Boolean biometricConsent,
            @RequestParam(value = "consentVersion", required = false, defaultValue = "1.0") String consentVersion,
            @RequestParam("photo") MultipartFile photo) {

        RegisterRequestDto req = new RegisterRequestDto();
        req.setName(name);
        req.setEmail(email);
        req.setLoginCode(loginCode);
        req.setRollNo(rollNo);
        req.setDepartment(department);
        req.setYear(year);
        req.setDivision(division);
        req.setPassword(password);
        req.setConfirmPassword(confirmPassword);
        req.setBiometricConsent(biometricConsent);
        req.setConsentVersion(consentVersion);

        UserSummaryDto createdUser = authService.register(req, photo);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponseDto> forgotPassword(@RequestBody ForgotPasswordRequestDto forgotRequest) {
        ForgotPasswordResponseDto response = authService.forgotPassword(forgotRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponseDto> resetPassword(@RequestBody ResetPasswordRequestDto resetRequest) {
        ResetPasswordResponseDto response = authService.resetPassword(resetRequest);
        return ResponseEntity.ok(response);
    }
}
