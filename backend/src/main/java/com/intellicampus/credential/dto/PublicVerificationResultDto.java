package com.intellicampus.credential.dto;

import com.intellicampus.credential.model.DocumentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PublicVerificationResultDto {
    private boolean valid;
    private String code;
    private String type;
    private String maskedStudentName;
    private String department;
    private LocalDate issueDate;
    private String fileHash;
    private DocumentStatus status;
    private String revokedReason;
    private String authorizedSignatory;
    private LocalDateTime verifiedAt;
    private String message;

    public PublicVerificationResultDto() {}

    public static PublicVerificationResultDto valid(String code, String type, String maskedName, String department, LocalDate issueDate, String fileHash, String authorizedSignatory) {
        PublicVerificationResultDto dto = new PublicVerificationResultDto();
        dto.setValid(true);
        dto.setCode(code);
        dto.setType(type);
        dto.setMaskedStudentName(maskedName);
        dto.setDepartment(department);
        dto.setIssueDate(issueDate);
        dto.setFileHash(fileHash);
        dto.setStatus(DocumentStatus.VALID);
        dto.setAuthorizedSignatory(authorizedSignatory);
        dto.setVerifiedAt(LocalDateTime.now());
        dto.setMessage("Official document verified successfully against IntelliCampus credential registry.");
        return dto;
    }

    public static PublicVerificationResultDto revoked(String code, String type, String maskedName, String department, LocalDate issueDate, String fileHash, String reason) {
        PublicVerificationResultDto dto = new PublicVerificationResultDto();
        dto.setValid(false);
        dto.setCode(code);
        dto.setType(type);
        dto.setMaskedStudentName(maskedName);
        dto.setDepartment(department);
        dto.setIssueDate(issueDate);
        dto.setFileHash(fileHash);
        dto.setStatus(DocumentStatus.REVOKED);
        dto.setRevokedReason(reason);
        dto.setVerifiedAt(LocalDateTime.now());
        dto.setMessage("Document was REVOKED by issuing authority. Reason: " + reason);
        return dto;
    }

    public static PublicVerificationResultDto notFound(String code) {
        PublicVerificationResultDto dto = new PublicVerificationResultDto();
        dto.setValid(false);
        dto.setCode(code);
        dto.setStatus(DocumentStatus.REVOKED);
        dto.setVerifiedAt(LocalDateTime.now());
        dto.setMessage("Invalid verification code. Document record not found in repository.");
        return dto;
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMaskedStudentName() { return maskedStudentName; }
    public void setMaskedStudentName(String maskedStudentName) { this.maskedStudentName = maskedStudentName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String revokedReason) { this.revokedReason = revokedReason; }

    public String getAuthorizedSignatory() { return authorizedSignatory; }
    public void setAuthorizedSignatory(String authorizedSignatory) { this.authorizedSignatory = authorizedSignatory; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
