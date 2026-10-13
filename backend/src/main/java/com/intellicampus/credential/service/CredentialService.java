package com.intellicampus.credential.service;

import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.audit.validation.HashChainValidator;
import com.intellicampus.credential.dto.*;
import com.intellicampus.credential.model.DocumentStatus;
import com.intellicampus.credential.model.VerifiableDocument;
import com.intellicampus.credential.repository.VerifiableDocumentRepository;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CredentialService {

    private final VerifiableDocumentRepository documentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final AuditChainService auditChainService;
    private final HashChainValidator hashChainValidator;

    public CredentialService(VerifiableDocumentRepository documentRepository,
                             StudentProfileRepository studentProfileRepository,
                             AuditChainService auditChainService,
                             HashChainValidator hashChainValidator) {
        this.documentRepository = documentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.auditChainService = auditChainService;
        this.hashChainValidator = hashChainValidator;
    }

    public DocumentResponseDto issueDocument(IssueDocumentRequestDto req) {
        if (req.getType() == null || req.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("Document type is required.");
        }

        StudentProfile student = null;
        if (req.getStudentId() != null) {
            student = studentProfileRepository.findById(req.getStudentId()).orElse(null);
        }

        String studentName = req.getStudentName();
        String rollNo = req.getRollNo();
        String dept = req.getDepartment();

        if (student != null) {
            if (studentName == null) studentName = student.getUser().getName();
            if (rollNo == null) rollNo = student.getRollNo();
            if (dept == null && student.getDivision() != null && student.getDivision().getDepartment() != null) {
                dept = student.getDivision().getDepartment().getName();
            }
        }

        if (studentName == null || studentName.trim().isEmpty()) {
            studentName = "Student";
        }

        // Generate unique DOC-YYYY-XXXXXXXX code
        String docCode = "DOC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        // Calculate cryptographic file hash if not pre-computed
        String fileHash = req.getFileHash();
        if (fileHash == null || fileHash.trim().isEmpty()) {
            String seed = docCode + "|" + studentName + "|" + rollNo + "|" + req.getType() + "|" + LocalDate.now();
            fileHash = AuditChainService.sha256Hex(seed);
        }

        String maskedName = maskName(studentName);
        LocalDate issueDate = req.getIssueDate() != null ? req.getIssueDate() : LocalDate.now();

        VerifiableDocument doc = new VerifiableDocument(
                docCode,
                req.getType(),
                student,
                studentName,
                maskedName,
                rollNo,
                dept,
                issueDate,
                fileHash,
                req.getGpa(),
                req.getAuthorizedSignatory() != null ? req.getAuthorizedSignatory() : "Registrar Office"
        );
        if (req.getAmountPaid() != null) {
            doc.setAmountPaid(req.getAmountPaid());
        }

        VerifiableDocument saved = documentRepository.save(doc);

        // Record on SHA-256 chained audit ledger
        auditChainService.recordEntry(
                req.getIssuerId() != null ? req.getIssuerId() : 1L,
                req.getIssuerName() != null ? req.getIssuerName() : "Registrar",
                "DOCUMENT_ISSUED",
                "DOCUMENT",
                docCode,
                "{}",
                String.format("{\"code\":\"%s\",\"type\":\"%s\",\"student\":\"%s\",\"hash\":\"%s\"}", docCode, req.getType(), studentName, fileHash),
                "Official credential issuance for " + req.getType()
        );

        return mapToDto(saved);
    }

    public DocumentResponseDto revokeDocument(String code, RevokeDocumentRequestDto req) {
        if (req == null) {
            throw new IllegalArgumentException("Revocation request cannot be null.");
        }
        hashChainValidator.validateRevocationReason(req.getReason());

        VerifiableDocument doc = documentRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with code: " + code));

        if (doc.getStatus() == DocumentStatus.REVOKED) {
            throw new IllegalStateException("Document is already revoked.");
        }

        doc.setStatus(DocumentStatus.REVOKED);
        String reason = req.getReason().trim();
        doc.setRevokedReason(reason);

        VerifiableDocument updated = documentRepository.save(doc);

        // Record revocation on cryptographic audit ledger
        auditChainService.recordEntry(
                req.getRevokedById() != null ? req.getRevokedById() : 1L,
                req.getRevokedByName() != null ? req.getRevokedByName() : "Dean/Authority",
                "DOCUMENT_REVOKED",
                "DOCUMENT",
                code,
                "{\"status\":\"VALID\"}",
                "{\"status\":\"REVOKED\",\"reason\":\"" + reason + "\"}",
                "Revocation: " + reason
        );

        return mapToDto(updated);
    }

    @Transactional(readOnly = true)
    public PublicVerificationResultDto verifyDocumentPublic(String code) {
        return documentRepository.findByCode(code)
                .map(doc -> {
                    if (doc.getStatus() == DocumentStatus.REVOKED) {
                        return PublicVerificationResultDto.revoked(
                                doc.getCode(),
                                doc.getType(),
                                doc.getMaskedStudentName(),
                                doc.getDepartment(),
                                doc.getIssueDate(),
                                doc.getFileHash(),
                                doc.getRevokedReason()
                        );
                    } else {
                        return PublicVerificationResultDto.valid(
                                doc.getCode(),
                                doc.getType(),
                                doc.getMaskedStudentName(),
                                doc.getDepartment(),
                                doc.getIssueDate(),
                                doc.getFileHash(),
                                doc.getAuthorizedSignatory()
                        );
                    }
                })
                .orElseGet(() -> PublicVerificationResultDto.notFound(code));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponseDto> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponseDto> getDocumentsForStudent(Long studentId) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        return documentRepository.findByStudentOrderByIssueDateDesc(student).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private String maskName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "***";
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() <= 1) {
                sb.append(p);
            } else {
                sb.append(p.charAt(0)).append("*".repeat(Math.min(p.length() - 1, 3)));
            }
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    private DocumentResponseDto mapToDto(VerifiableDocument doc) {
        DocumentResponseDto dto = new DocumentResponseDto();
        dto.setId(doc.getId());
        dto.setCode(doc.getCode());
        dto.setType(doc.getType());
        if (doc.getStudent() != null) {
            dto.setStudentId(doc.getStudent().getId());
        }
        dto.setStudentName(doc.getStudentName());
        dto.setMaskedStudentName(doc.getMaskedStudentName());
        dto.setRollNo(doc.getRollNo());
        dto.setDepartment(doc.getDepartment());
        dto.setIssueDate(doc.getIssueDate());
        dto.setFileHash(doc.getFileHash());
        dto.setStatus(doc.getStatus());
        dto.setRevokedReason(doc.getRevokedReason());
        dto.setGpa(doc.getGPA());
        dto.setAmountPaid(doc.getAmountPaid());
        dto.setAuthorizedSignatory(doc.getAuthorizedSignatory());
        dto.setCreatedAt(doc.getCreatedAt());
        return dto;
    }
}
