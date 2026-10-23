package com.intellicampus.audit.controller;

import com.intellicampus.audit.dto.AuditLogResponseDto;
import com.intellicampus.audit.dto.AuditVerificationResponseDto;
import com.intellicampus.audit.service.AuditChainService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditChainService auditChainService;

    public AuditController(AuditChainService auditChainService) {
        this.auditChainService = auditChainService;
    }

    @GetMapping({"/chain", "/logs"})
    public ResponseEntity<List<AuditLogResponseDto>> getAuditChain() {
        return ResponseEntity.ok(auditChainService.getChainEntries());
    }

    @GetMapping("/verify")
    public ResponseEntity<AuditVerificationResponseDto> verifyAuditChain() {
        return ResponseEntity.ok(auditChainService.verifyChain());
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<AuditLogResponseDto>> getEntityAuditTrail(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        return ResponseEntity.ok(auditChainService.getEntriesForEntity(entityType, entityId));
    }
}
