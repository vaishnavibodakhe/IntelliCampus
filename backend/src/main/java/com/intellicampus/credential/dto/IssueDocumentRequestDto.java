package com.intellicampus.credential.dto;

import java.time.LocalDate;

public class IssueDocumentRequestDto {
    private String type; // e.g. "TRANSCRIPT", "BONAFIDE", "DEGREE", "FEE_RECEIPT"
    private Long studentId;
    private String studentName;
    private String rollNo;
    private String department;
    private LocalDate issueDate;
    private String fileHash;
    private String gpa;
    private String amountPaid;
    private String authorizedSignatory;
    private Long issuerId;
    private String issuerName;

    public IssueDocumentRequestDto() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getGpa() { return gpa; }
    public void setGpa(String gpa) { this.gpa = gpa; }

    public String getAmountPaid() { return amountPaid; }
    public void setAmountPaid(String amountPaid) { this.amountPaid = amountPaid; }

    public String getAuthorizedSignatory() { return authorizedSignatory; }
    public void setAuthorizedSignatory(String authorizedSignatory) { this.authorizedSignatory = authorizedSignatory; }

    public Long getIssuerId() { return issuerId; }
    public void setIssuerId(Long issuerId) { this.issuerId = issuerId; }

    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
}
