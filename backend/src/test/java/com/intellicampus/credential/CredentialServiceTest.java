package com.intellicampus.credential;

import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.audit.validation.HashChainValidator;
import com.intellicampus.credential.dto.*;
import com.intellicampus.credential.model.DocumentStatus;
import com.intellicampus.credential.model.VerifiableDocument;
import com.intellicampus.credential.repository.VerifiableDocumentRepository;
import com.intellicampus.credential.service.CredentialService;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for CredentialService covering document issuance,
 * tamper-resistant revocation enforcement, and public verification contracts.
 * Compliant with IC-SRS-1.0 Section 8.7 and Test Case TC-12.
 */
@ExtendWith(MockitoExtension.class)
class CredentialServiceTest {

    @Mock
    private VerifiableDocumentRepository documentRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private AuditChainService auditChainService;

    @Spy
    private HashChainValidator hashChainValidator = new HashChainValidator();

    @InjectMocks
    private CredentialService credentialService;

    private User studentUser;
    private StudentProfile studentProfile;
    private VerifiableDocument validDocument;

    @BeforeEach
    void setUp() {
        studentUser = new User("STU-100", Role.STUDENT, "Aarav Ramesh Mehta", "aarav@intellicampus.edu", "hash", null);
        ReflectionTestUtils.setField(studentUser, "id", 1L);

        studentProfile = new StudentProfile(studentUser, "2024-CS-042");
        ReflectionTestUtils.setField(studentProfile, "id", 10L);

        validDocument = new VerifiableDocument(
                "DOC-2026-A1B2C3D4E5",
                "Bonafide Certificate",
                studentProfile,
                "Aarav Ramesh Mehta",
                "A*** R*** M***",
                "2024-CS-042",
                "Computer Science",
                LocalDate.now(),
                "3a7ac6497a7e857a0477cc606f1842f819d708add7a7e1d6cf9f0869a082156b",
                9.12,
                "Office of the Registrar"
        );
        ReflectionTestUtils.setField(validDocument, "id", 101L);
    }

    @Nested
    @DisplayName("Public Verification Contracts (VALID vs REVOKED vs NOT_FOUND)")
    class PublicVerificationTests {

        @Test
        @DisplayName("Criterion 2: Public verification returns VALID for an active, unrevoked document")
        void testVerifyActiveDocumentReturnsValid() {
            when(documentRepository.findByCode("DOC-2026-A1B2C3D4E5")).thenReturn(Optional.of(validDocument));

            PublicVerificationResultDto result = credentialService.verifyDocumentPublic("DOC-2026-A1B2C3D4E5");

            assertNotNull(result);
            assertTrue(result.getIsValid(), "Active document must have isValid = true");
            assertEquals("VALID", result.getStatus());
            assertEquals("DOC-2026-A1B2C3D4E5", result.getCode());
            assertEquals("Bonafide Certificate", result.getType());
            assertEquals("A*** R*** M***", result.getMaskedStudentName(), "Student name must be privacy-masked");
            assertEquals("Computer Science", result.getDepartment());
            assertEquals("Office of the Registrar", result.getSignatory());
            assertNull(result.getRevocationReason(), "Active document should not have revocation reason");
        }

        @Test
        @DisplayName("Criterion 2: Public verification returns REVOKED for a revoked document with revocation justification")
        void testVerifyRevokedDocumentReturnsRevoked() {
            validDocument.setStatus(DocumentStatus.REVOKED);
            validDocument.setRevokedReason("Student suspended due to academic misconduct");

            when(documentRepository.findByCode("DOC-2026-A1B2C3D4E5")).thenReturn(Optional.of(validDocument));

            PublicVerificationResultDto result = credentialService.verifyDocumentPublic("DOC-2026-A1B2C3D4E5");

            assertNotNull(result);
            assertFalse(result.getIsValid(), "Revoked document must have isValid = false");
            assertEquals("REVOKED", result.getStatus());
            assertEquals("Student suspended due to academic misconduct", result.getRevocationReason());
            assertEquals("DOC-2026-A1B2C3D4E5", result.getCode());
        }

        @Test
        @DisplayName("Public verification returns NOT_FOUND for invalid or non-existent document codes")
        void testVerifyNonExistentDocumentReturnsNotFound() {
            when(documentRepository.findByCode("DOC-UNKNOWN-999")).thenReturn(Optional.empty());

            PublicVerificationResultDto result = credentialService.verifyDocumentPublic("DOC-UNKNOWN-999");

            assertNotNull(result);
            assertFalse(result.getIsValid());
            assertEquals("NOT_FOUND", result.getStatus());
            assertEquals("DOC-UNKNOWN-999", result.getCode());
        }
    }

    @Nested
    @DisplayName("Document Revocation & Mandatory Justification Enforcement")
    class RevocationTests {

        @Test
        @DisplayName("Should successfully revoke document when reason meets minimum 10-char policy and append to audit chain")
        void testRevokeDocumentSuccess() {
            RevokeDocumentRequestDto req = new RevokeDocumentRequestDto();
            req.setReason("Credential cancelled after student admission transfer to Pune University");
            req.setRevokedById(5L);
            req.setRevokedByName("Dean Academics");

            when(documentRepository.findByCode("DOC-2026-A1B2C3D4E5")).thenReturn(Optional.of(validDocument));
            when(documentRepository.save(any(VerifiableDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            DocumentResponseDto response = credentialService.revokeDocument("DOC-2026-A1B2C3D4E5", req);

            assertNotNull(response);
            assertEquals(DocumentStatus.REVOKED, response.getStatus());
            assertEquals("Credential cancelled after student admission transfer to Pune University", response.getRevokedReason());

            verify(documentRepository).save(validDocument);
            verify(auditChainService).recordEntry(
                    eq(5L),
                    eq("Dean Academics"),
                    eq("DOCUMENT_REVOKED"),
                    eq("DOCUMENT"),
                    eq("DOC-2026-A1B2C3D4E5"),
                    contains("VALID"),
                    contains("REVOKED"),
                    contains("Credential cancelled")
            );
        }

        @Test
        @DisplayName("Should reject document revocation when justification reason is under 10 characters")
        void testRevokeDocumentShortReasonThrowsException() {
            RevokeDocumentRequestDto req = new RevokeDocumentRequestDto();
            req.setReason("Typo error"); // Only 10 characters with space, let's test short: "Typo" (4 chars)
            req.setReason("Typo");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    credentialService.revokeDocument("DOC-2026-A1B2C3D4E5", req)
            );
            assertTrue(ex.getMessage().contains("at least 10 characters"));
            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject revocation if document is already revoked")
        void testCannotRevokeAlreadyRevokedDocument() {
            validDocument.setStatus(DocumentStatus.REVOKED);
            RevokeDocumentRequestDto req = new RevokeDocumentRequestDto();
            req.setReason("Administrative second revocation attempt");

            when(documentRepository.findByCode("DOC-2026-A1B2C3D4E5")).thenReturn(Optional.of(validDocument));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    credentialService.revokeDocument("DOC-2026-A1B2C3D4E5", req)
            );
            assertTrue(ex.getMessage().contains("already revoked"));
        }
    }
}
