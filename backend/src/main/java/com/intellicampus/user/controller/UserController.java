package com.intellicampus.user.controller;

import com.intellicampus.user.dto.AdminStaffResetPasswordDto;
import com.intellicampus.user.dto.CreateUserRequestDto;
import com.intellicampus.user.dto.CsvImportResultDto;
import com.intellicampus.user.dto.UserResponseDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDto>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role) {
        return ResponseEntity.ok(userService.getAllUsers(search, role));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@RequestBody CreateUserRequestDto req) {
        if (req.getRole() == Role.PRINCIPAL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Creation of PRINCIPAL accounts via API is strictly prohibited.");
        }
        if (req.getRole() == Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Creation of STUDENT accounts via Principal console is strictly prohibited. Students register themselves on the Student portal.");
        }
        UserResponseDto created = userService.createUser(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping({"/csv-import", "/import-csv"})
    public ResponseEntity<CsvImportResultDto> importUsersCsv(@RequestBody List<String> csvRows) {
        CsvImportResultDto result = userService.importCsv(csvRows);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, String>> updateUserStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, String> body) {
        String targetStatus = status;
        if (targetStatus == null && body != null && body.containsKey("status")) {
            targetStatus = body.get("status");
        }
        if (targetStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status parameter is required.");
        }
        UserStatus userStatus = UserStatus.valueOf(targetStatus.toUpperCase());
        userService.updateUserStatus(id, userStatus);
        return ResponseEntity.ok(Map.of("message", "User status updated successfully", "status", userStatus.name()));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetUserPassword(
            @PathVariable Long id,
            @RequestBody(required = false) AdminStaffResetPasswordDto req) {
        String newPw = req != null ? req.getNewPassword() : null;
        userService.resetUserPassword(id, newPw);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }
}
