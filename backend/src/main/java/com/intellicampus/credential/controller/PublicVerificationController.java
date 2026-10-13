package com.intellicampus.credential.controller;

import com.intellicampus.credential.dto.PublicVerificationResultDto;
import com.intellicampus.credential.service.CredentialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = "*")
public class PublicVerificationController {

    private final CredentialService credentialService;

    public PublicVerificationController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping("/verify/{code}")
    public ResponseEntity<PublicVerificationResultDto> verifyPublic(@PathVariable String code) {
        return ResponseEntity.ok(credentialService.verifyDocumentPublic(code));
    }
}
