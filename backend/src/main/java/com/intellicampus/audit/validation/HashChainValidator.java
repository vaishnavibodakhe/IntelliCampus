package com.intellicampus.audit.validation;

import com.intellicampus.audit.model.AuditLogEntry;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Validates cryptographic SHA-256 hash chain integrity and document revocation policies.
 * Compliant with IC-SRS-1.0 Section 8.7 and Test Cases TC-11 & TC-12.
 */
@Component
public class HashChainValidator {

    public static final String GENESIS_PREV_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    public static final int MIN_REVOCATION_REASON_LENGTH = 10;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static class ChainValidationResult {
        private final boolean valid;
        private final int totalBlocks;
        private final Integer tamperedIndex;
        private final Long tamperedEntryId;
        private final String message;

        public ChainValidationResult(boolean valid, int totalBlocks, Integer tamperedIndex, Long tamperedEntryId, String message) {
            this.valid = valid;
            this.totalBlocks = totalBlocks;
            this.tamperedIndex = tamperedIndex;
            this.tamperedEntryId = tamperedEntryId;
            this.message = message;
        }

        public static ChainValidationResult valid(int totalBlocks) {
            return new ChainValidationResult(true, totalBlocks, null, null, "Cryptographic audit hash chain is intact and uncorrupted.");
        }

        public static ChainValidationResult invalid(int totalBlocks, int tamperedIndex, Long tamperedEntryId, String message) {
            return new ChainValidationResult(false, totalBlocks, tamperedIndex, tamperedEntryId, message);
        }

        public boolean isValid() { return valid; }
        public int getTotalBlocks() { return totalBlocks; }
        public Integer getTamperedIndex() { return tamperedIndex; }
        public Long getTamperedEntryId() { return tamperedEntryId; }
        public String getMessage() { return message; }
    }

    /**
     * Criterion 1 & 2 & 3: Verifies complete audit ledger chain linkage and entry hashes.
     * Detects and reports the exact tampered block index if content or hash link is corrupted.
     *
     * @param entries Chronologically sorted audit ledger entries
     * @return ChainValidationResult
     */
    public ChainValidationResult validateChain(List<AuditLogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return ChainValidationResult.valid(0);
        }

        String expectedPrevHash = GENESIS_PREV_HASH;

        for (int i = 0; i < entries.size(); i++) {
            AuditLogEntry current = entries.get(i);

            // Criterion 2: Validate prevHash of block i matches entryHash of block i-1
            if (!current.getPrevHash().equalsIgnoreCase(expectedPrevHash)) {
                return ChainValidationResult.invalid(
                        entries.size(),
                        i,
                        current.getId(),
                        String.format("Broken hash link detected at block index %d (Block #%d). Expected prevHash '%s' but found '%s'.",
                                i, current.getId(), expectedPrevHash, current.getPrevHash())
                );
            }

            // Criterion 1: Validate entryHash == SHA-256(prevHash | actorId | action | entityType | entityId | oldVal | newVal | reason | createdAt)
            String createdAtStr = current.getCreatedAt() != null ? current.getCreatedAt().format(ISO_FORMATTER) : "";
            String calculatedHash = calculateEntryHash(
                    current.getPrevHash(),
                    current.getActorId(),
                    current.getAction(),
                    current.getEntityType(),
                    current.getEntityId(),
                    current.getOldValue(),
                    current.getNewValue(),
                    current.getReason(),
                    createdAtStr
            );

            if (!current.getEntryHash().equalsIgnoreCase(calculatedHash)) {
                return ChainValidationResult.invalid(
                        entries.size(),
                        i,
                        current.getId(),
                        String.format("Payload corruption / tamper detected at block index %d (Block #%d). Stored hash '%s' does not match computed hash '%s'.",
                                i, current.getId(), current.getEntryHash(), calculatedHash)
                );
            }

            expectedPrevHash = current.getEntryHash();
        }

        return ChainValidationResult.valid(entries.size());
    }

    /**
     * Criterion 1: Computes canonical SHA-256 audit entry hash:
     * SHA-256(prevHash + "|" + actorId + "|" + action + "|" + entityType + "|" + entityId + "|" + oldVal + "|" + newVal + "|" + reason + "|" + createdAt)
     */
    public String calculateEntryHash(String prevHash,
                                     Long actorId,
                                     String action,
                                     String entityType,
                                     String entityId,
                                     String oldValue,
                                     String newValue,
                                     String reason,
                                     String createdAtStr) {
        String payload = String.join("|",
                prevHash != null ? prevHash : GENESIS_PREV_HASH,
                actorId != null ? String.valueOf(actorId) : "0",
                action != null ? action : "UNKNOWN",
                entityType != null ? entityType : "GENERIC",
                entityId != null ? entityId : "0",
                oldValue != null ? oldValue : "{}",
                newValue != null ? newValue : "{}",
                reason != null ? reason : "",
                createdAtStr != null ? createdAtStr : ""
        );

        return sha256Hex(payload);
    }

    /**
     * Criterion 4: Validate document revocation reason has minimum length of 10 characters.
     *
     * @param reason The justification provided for revoking an official credential
     * @throws IllegalArgumentException If reason is null, empty, or shorter than 10 characters
     */
    public void validateRevocationReason(String reason) {
        if (reason == null || reason.trim().length() < MIN_REVOCATION_REASON_LENGTH) {
            int len = (reason == null) ? 0 : reason.trim().length();
            throw new IllegalArgumentException(
                    String.format("Document revocation justification is mandatory and must contain at least %d characters (provided: %d characters).",
                            MIN_REVOCATION_REASON_LENGTH, len)
            );
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in Java runtime", e);
        }
    }
}
