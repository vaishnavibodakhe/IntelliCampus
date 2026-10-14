package com.intellicampus.audit;

import com.intellicampus.audit.dto.AuditVerificationResponseDto;
import com.intellicampus.audit.model.AuditLogEntry;
import com.intellicampus.audit.repository.AuditLogEntryRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.audit.validation.HashChainValidator;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for AuditChainService and cryptographic ledger tamper detection.
 * Compliant with IC-SRS-1.0 Section 8.7 and Test Cases TC-11 & TC-12.
 */
@ExtendWith(MockitoExtension.class)
class AuditChainServiceTest {

    @Mock
    private AuditLogEntryRepository auditRepository;

    @Spy
    private HashChainValidator hashChainValidator = new HashChainValidator();

    @InjectMocks
    private AuditChainService auditChainService;

    private static final String GENESIS = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @BeforeEach
    void setUp() {
    }

    private AuditLogEntry createValidEntry(Long id, String prevHash, Long actorId, String actorName,
                                          String action, String entityType, String entityId,
                                          String oldVal, String newVal, String reason, LocalDateTime createdAt) {
        String createdAtStr = createdAt.format(ISO_FORMATTER);
        String calculatedHash = hashChainValidator.calculateEntryHash(
                prevHash, actorId, action, entityType, entityId, oldVal, newVal, reason, createdAtStr
        );

        AuditLogEntry entry = new AuditLogEntry(
                prevHash, calculatedHash, actorId, actorName, action, entityType, entityId, oldVal, newVal, reason
        );
        entry.setCreatedAt(createdAt);
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    @Nested
    @DisplayName("Cryptographic Ledger Integrity & Tamper Detection Tests")
    class TamperDetectionTests {

        @Test
        @DisplayName("Criterion 1: Chain verification succeeds for an authentic, uncorrupted audit chain")
        void testAuthenticChainVerification() {
            LocalDateTime now = LocalDateTime.now();
            AuditLogEntry block0 = createValidEntry(1L, GENESIS, 1L, "Admin", "USER_CREATED", "USER", "101", "{}", "{\"name\":\"Aarav\"}", "Initial import", now.minusMinutes(10));
            AuditLogEntry block1 = createValidEntry(2L, block0.getEntryHash(), 2L, "Faculty", "MARK_ENTERED", "MARK", "501", "{}", "{\"marks\":25.0}", "Unit test evaluation", now.minusMinutes(5));
            AuditLogEntry block2 = createValidEntry(3L, block1.getEntryHash(), 3L, "HOD", "MARK_CORRECTION_APPROVED", "MARK", "501", "{\"marks\":25.0}", "{\"marks\":28.0}", "Question 3 re-check", now.minusMinutes(1));

            when(auditRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(block0, block1, block2));

            AuditVerificationResponseDto result = auditChainService.verifyChain();

            assertTrue(result.isValid(), "Cryptographic audit hash chain must be reported valid");
            assertEquals(3, result.getTotalBlocks());
            assertEquals(block2.getEntryHash(), result.getLastHash());
            assertNull(result.getTamperedBlockIndex());
            assertNull(result.getTamperedEntryId());
        }

        @Test
        @DisplayName("Criterion 1: Modifying a single character in any historical payload invalidates chain integrity")
        void testModifyingSingleCharacterInPayloadInvalidatesChain() {
            LocalDateTime now = LocalDateTime.now();
            AuditLogEntry block0 = createValidEntry(1L, GENESIS, 1L, "Admin", "USER_CREATED", "USER", "101", "{}", "{\"name\":\"Aarav\"}", "Initial import", now.minusMinutes(10));
            AuditLogEntry block1 = createValidEntry(2L, block0.getEntryHash(), 2L, "Faculty", "MARK_ENTERED", "MARK", "501", "{}", "{\"marks\":25.0}", "Unit test evaluation", now.minusMinutes(5));
            AuditLogEntry block2 = createValidEntry(3L, block1.getEntryHash(), 3L, "HOD", "MARK_CORRECTION_APPROVED", "MARK", "501", "{\"marks\":25.0}", "{\"marks\":28.0}", "Question 3 re-check", now.minusMinutes(1));

            // Malicious actor modifies a single character in block 1: changing 25.0 -> 25.5
            block1.setNewValue("{\"marks\":25.5}");

            when(auditRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(block0, block1, block2));

            AuditVerificationResponseDto result = auditChainService.verifyChain();

            assertFalse(result.isValid(), "Tampered chain must be reported invalid");
            assertEquals(3, result.getTotalBlocks());
            assertEquals(1, result.getTamperedBlockIndex(), "Should pinpoint the exact corrupted block index (1)");
            assertEquals(2L, result.getTamperedEntryId(), "Should identify corrupted entry ID 2");
            assertTrue(result.getMessage().contains("Payload corruption / tamper detected"), "Error should cite payload tamper");
        }

        @Test
        @DisplayName("Criterion 1: Breaking previous block hash link invalidates chain integrity")
        void testBrokenHashLinkInvalidatesChain() {
            LocalDateTime now = LocalDateTime.now();
            AuditLogEntry block0 = createValidEntry(1L, GENESIS, 1L, "Admin", "USER_CREATED", "USER", "101", "{}", "{}", "User created", now.minusMinutes(10));
            AuditLogEntry block1 = createValidEntry(2L, block0.getEntryHash(), 2L, "Registrar", "DOCUMENT_ISSUED", "DOC", "DOC-001", "{}", "{}", "Official cert", now.minusMinutes(5));

            // Malicious actor tampers with prevHash pointer on block 1
            block1.setPrevHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

            when(auditRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(block0, block1));

            AuditVerificationResponseDto result = auditChainService.verifyChain();

            assertFalse(result.isValid());
            assertEquals(1, result.getTamperedBlockIndex(), "Should identify block 1 as having broken linkage");
            assertTrue(result.getMessage().contains("Broken hash link detected"));
        }

        @Test
        @DisplayName("Empty ledger returns valid with 0 blocks")
        void testEmptyLedger() {
            when(auditRepository.findAllByOrderByCreatedAtAsc()).thenReturn(new ArrayList<>());

            AuditVerificationResponseDto result = auditChainService.verifyChain();

            assertTrue(result.isValid());
            assertEquals(0, result.getTotalBlocks());
        }
    }

    @Nested
    @DisplayName("Record Entry Tests")
    class RecordEntryTests {

        @Test
        @DisplayName("Recording entry links to latest block hash and stores computed SHA-256 hash")
        void testRecordEntryLinksToLatestBlock() {
            AuditLogEntry previousEntry = new AuditLogEntry(
                    GENESIS, "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef",
                    1L, "Registrar", "DOC_ISSUED", "DOC", "DOC-101", "{}", "{}", "Issuing official degree"
            );
            ReflectionTestUtils.setField(previousEntry, "id", 1L);

            when(auditRepository.findTopByOrderByCreatedAtDesc()).thenReturn(java.util.Optional.of(previousEntry));
            when(auditRepository.save(any(AuditLogEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            AuditLogEntry newEntry = auditChainService.recordEntry(
                    2L, "Dean", "DOC_REVOKED", "DOC", "DOC-101",
                    "{\"status\":\"VALID\"}", "{\"status\":\"REVOKED\"}", "Revoked due to falsification"
            );

            assertNotNull(newEntry);
            assertEquals(previousEntry.getEntryHash(), newEntry.getPrevHash(), "New block prevHash must equal previous block entryHash");
            assertNotNull(newEntry.getEntryHash());
            assertEquals(64, newEntry.getEntryHash().length(), "SHA-256 hash must be 64 hexadecimal characters");
            verify(auditRepository).save(any(AuditLogEntry.class));
        }
    }
}
