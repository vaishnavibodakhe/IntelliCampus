package com.intellicampus.credential.controller;

import com.intellicampus.credential.dto.*;
import com.intellicampus.credential.service.CredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credentials")
@CrossOrigin(origins = "*")
public class CredentialController {

    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/issue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentResponseDto> issueDocument(@RequestBody IssueDocumentRequestDto req) {
        DocumentResponseDto issued = credentialService.issueDocument(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(issued);
    }

    @PutMapping("/{code}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentResponseDto> revokeDocument(
            @PathVariable String code,
            @RequestBody RevokeDocumentRequestDto req) {
        return ResponseEntity.ok(credentialService.revokeDocument(code, req));
    }

    @GetMapping("/verify/{code}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<PublicVerificationResultDto> verifyDocument(@PathVariable String code) {
        return ResponseEntity.ok(credentialService.verifyDocumentPublic(code));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD')")
    public ResponseEntity<List<DocumentResponseDto>> getAllDocuments() {
        return ResponseEntity.ok(credentialService.getAllDocuments());
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD', 'FACULTY', 'MENTOR', 'STUDENT')")
    public ResponseEntity<List<DocumentResponseDto>> getStudentDocuments(@PathVariable Long studentId) {
        return ResponseEntity.ok(credentialService.getDocumentsForStudent(studentId));
    }
}
