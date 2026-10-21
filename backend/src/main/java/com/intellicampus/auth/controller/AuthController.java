package com.intellicampus.auth.controller;

import com.intellicampus.auth.dto.AuthResponseDto;
import com.intellicampus.auth.dto.LoginRequestDto;
import com.intellicampus.auth.dto.RegisterRequestDto;
import com.intellicampus.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("legacyAuthController")
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173", "*"})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> registerStudent(@Valid @RequestBody RegisterRequestDto req) {
        AuthResponseDto response = authService.registerStudent(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto req) {
        AuthResponseDto response = authService.login(req);
        return ResponseEntity.ok(response);
    }
}
