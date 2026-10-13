package com.intellicampus.audit.dto;

public class AuditVerificationResponseDto {
    private boolean valid;
    private int totalEntries;
    private String lastVerifiedHash;
    private Integer tamperedIndex;
    private Long tamperedEntryId;
    private String message;

    public AuditVerificationResponseDto() {}

    public AuditVerificationResponseDto(boolean valid, int totalEntries, String lastVerifiedHash, Integer tamperedIndex, Long tamperedEntryId, String message) {
        this.valid = valid;
        this.totalEntries = totalEntries;
        this.lastVerifiedHash = lastVerifiedHash;
        this.tamperedIndex = tamperedIndex;
        this.tamperedEntryId = tamperedEntryId;
        this.message = message;
    }

    public static AuditVerificationResponseDto valid(int totalEntries, String lastVerifiedHash) {
        return new AuditVerificationResponseDto(true, totalEntries, lastVerifiedHash, null, null, "Audit ledger cryptographic integrity verified. No tampering detected.");
    }

    public static AuditVerificationResponseDto invalid(int totalEntries, int tamperedIndex, Long tamperedEntryId, String reason) {
        return new AuditVerificationResponseDto(false, totalEntries, null, tamperedIndex, tamperedEntryId, "Audit chain integrity violation: " + reason);
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public int getTotalEntries() { return totalEntries; }
    public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }

    public String getLastVerifiedHash() { return lastVerifiedHash; }
    public void setLastVerifiedHash(String lastVerifiedHash) { this.lastVerifiedHash = lastVerifiedHash; }

    public Integer getTamperedIndex() { return tamperedIndex; }
    public void setTamperedIndex(Integer tamperedIndex) { this.tamperedIndex = tamperedIndex; }

    public Long getTamperedEntryId() { return tamperedEntryId; }
    public void setTamperedEntryId(Long tamperedEntryId) { this.tamperedEntryId = tamperedEntryId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
