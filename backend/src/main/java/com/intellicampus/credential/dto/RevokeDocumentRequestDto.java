package com.intellicampus.credential.dto;

public class RevokeDocumentRequestDto {
    private String reason;
    private Long revokedById;
    private String revokedByName;

    public RevokeDocumentRequestDto() {}

    public RevokeDocumentRequestDto(String reason, Long revokedById, String revokedByName) {
        this.reason = reason;
        this.revokedById = revokedById;
        this.revokedByName = revokedByName;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getRevokedById() { return revokedById; }
    public void setRevokedById(Long revokedById) { this.revokedById = revokedById; }

    public String getRevokedByName() { return revokedByName; }
    public void setRevokedByName(String revokedByName) { this.revokedByName = revokedByName; }
}
