package com.intellicampus.user.controller;

import com.intellicampus.user.dto.AuthResponseDto;
import com.intellicampus.user.dto.LoginRequestDto;
import com.intellicampus.user.dto.StudentRegisterRequestDto;
import com.intellicampus.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/student/register")
    public ResponseEntity<AuthResponseDto> registerStudent(@RequestBody StudentRegisterRequestDto req) {
        AuthResponseDto resp = authService.registerStudent(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@RequestBody StudentRegisterRequestDto req) {
        return registerStudent(req);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(
            @RequestBody LoginRequestDto req,
            jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        AuthResponseDto resp = authService.login(req, clientIp);
        return ResponseEntity.ok(resp);
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> resp = authService.logout(authHeader);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> resp = authService.getCurrentUserProfile(authHeader);
        return ResponseEntity.ok(resp);
    }
}
