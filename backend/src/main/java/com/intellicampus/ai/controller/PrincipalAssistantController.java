package com.intellicampus.ai.controller;

import com.intellicampus.ai.dto.PrincipalAssistantQueryDto;
import com.intellicampus.ai.dto.PrincipalAssistantResponseDto;
import com.intellicampus.ai.service.PrincipalAssistantService;
import com.intellicampus.security.util.SecurityUtils;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/principal/assistant")
@PreAuthorize("hasRole('PRINCIPAL')")
@CrossOrigin(origins = "*")
public class PrincipalAssistantController {

    private final PrincipalAssistantService assistantService;
    private final SecurityUtils securityUtils;

    public PrincipalAssistantController(
            PrincipalAssistantService assistantService,
            SecurityUtils securityUtils) {
        this.assistantService = assistantService;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/query")
    public ResponseEntity<PrincipalAssistantResponseDto> queryAssistant(
            @Valid @RequestBody PrincipalAssistantQueryDto queryDto) {
        User currentUser = securityUtils.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated"));

        if (currentUser.getRole() != Role.PRINCIPAL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Principal role required");
        }

        PrincipalAssistantResponseDto response = assistantService.queryAssistant(queryDto, currentUser);
        return ResponseEntity.ok(response);
    }
}
