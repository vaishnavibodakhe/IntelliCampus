package com.intellicampus.credential.dto;

import com.intellicampus.credential.model.DocumentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DocumentResponseDto {
    private Long id;
    private String code;
    private String type;
    private Long studentId;
    private String studentName;
    private String maskedStudentName;
    private String rollNo;
    private String department;
    private LocalDate issueDate;
    private String fileHash;
    private DocumentStatus status;
    private String revokedReason;
    private String gpa;
    private String amountPaid;
    private String authorizedSignatory;
    private LocalDateTime createdAt;

    public DocumentResponseDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getMaskedStudentName() { return maskedStudentName; }
    public void setMaskedStudentName(String maskedStudentName) { this.maskedStudentName = maskedStudentName; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

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

    public String getGpa() { return gpa; }
    public void setGpa(String gpa) { this.gpa = gpa; }

    public String getAmountPaid() { return amountPaid; }
    public void setAmountPaid(String amountPaid) { this.amountPaid = amountPaid; }

    public String getAuthorizedSignatory() { return authorizedSignatory; }
    public void setAuthorizedSignatory(String authorizedSignatory) { this.authorizedSignatory = authorizedSignatory; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
