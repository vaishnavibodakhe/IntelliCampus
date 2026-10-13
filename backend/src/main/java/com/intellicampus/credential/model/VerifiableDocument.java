package com.intellicampus.credential.model;

import com.intellicampus.user.model.StudentProfile;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "issued_documents", indexes = {
    @Index(name = "idx_docs_code", columnList = "code", unique = true),
    @Index(name = "idx_docs_file_hash", columnList = "file_hash"),
    @Index(name = "idx_docs_status", columnList = "status"),
    @Index(name = "idx_docs_student", columnList = "student_id")
})
public class VerifiableDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 60)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private StudentProfile student;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "masked_student_name", nullable = false, length = 100)
    private String maskedStudentName;

    @Column(name = "roll_no", length = 30)
    private String rollNo;

    @Column(length = 100)
    private String department;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.VALID;

    @Column(name = "revoked_reason", length = 255)
    private String revokedReason;

    @Column(length = 30)
    private String gpa;

    @Column(name = "amount_paid", length = 50)
    private String amountPaid;

    @Column(name = "authorized_signatory", length = 120)
    private String authorizedSignatory;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public VerifiableDocument() {}

    public VerifiableDocument(String code, String type, StudentProfile student, String studentName, String maskedStudentName, String rollNo, String department, LocalDate issueDate, String fileHash, String gpa, String authorizedSignatory) {
        this.code = code;
        this.type = type;
        this.student = student;
        this.studentName = studentName;
        this.maskedStudentName = maskedStudentName;
        this.rollNo = rollNo;
        this.department = department;
        this.issueDate = issueDate != null ? issueDate : LocalDate.now();
        this.fileHash = fileHash;
        this.status = DocumentStatus.VALID;
        this.gpa = gpa;
        this.authorizedSignatory = authorizedSignatory;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public StudentProfile getStudent() { return student; }
    public void setStudent(StudentProfile student) { this.student = student; }

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

    public String getGPA() { return gpa; }
    public void setGPA(String gpa) { this.gpa = gpa; }

    public String getAmountPaid() { return amountPaid; }
    public void setAmountPaid(String amountPaid) { this.amountPaid = amountPaid; }

    public String getAuthorizedSignatory() { return authorizedSignatory; }
    public void setAuthorizedSignatory(String authorizedSignatory) { this.authorizedSignatory = authorizedSignatory; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
