package com.intellicampus.user.controller;

import com.intellicampus.user.dto.CreateUserRequestDto;
import com.intellicampus.user.dto.CsvImportResultDto;
import com.intellicampus.user.dto.UserResponseDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
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
        if (req.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Creation of ADMIN accounts via API is strictly prohibited.");
        }
        UserResponseDto created = userService.createUser(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/csv-import")
    public ResponseEntity<CsvImportResultDto> importUsersCsv(@RequestBody List<String> csvRows) {
        CsvImportResultDto result = userService.importCsv(csvRows);
        return ResponseEntity.ok(result);
    }
}
