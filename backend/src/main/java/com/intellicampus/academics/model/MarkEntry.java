package com.intellicampus.academics.model;

import com.intellicampus.academic.model.Subject;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mark_entries", indexes = {
    @Index(name = "idx_marks_student", columnList = "student_id"),
    @Index(name = "idx_marks_subject", columnList = "subject_id"),
    @Index(name = "idx_marks_status", columnList = "status")
})
public class MarkEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false, length = 60)
    private String exam;

    @Column(nullable = false)
    private Double marks;

    @Column(name = "max_marks", nullable = false)
    private Double maxMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MarkStatus status = MarkStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MarkEntry() {}

    public MarkEntry(StudentProfile student, Subject subject, String exam, Double marks, Double maxMarks, MarkStatus status, User updatedBy) {
        this.student = student;
        this.subject = subject;
        this.exam = exam;
        this.marks = marks;
        this.maxMarks = maxMarks;
        this.status = status != null ? status : MarkStatus.DRAFT;
        this.updatedBy = updatedBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StudentProfile getStudent() { return student; }
    public void setStudent(StudentProfile student) { this.student = student; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public String getExam() { return exam; }
    public void setExam(String exam) { this.exam = exam; }

    public Double getMarks() { return marks; }
    public void setMarks(Double marks) { this.marks = marks; }

    public Double getMaxMarks() { return maxMarks; }
    public void setMaxMarks(Double maxMarks) { this.maxMarks = maxMarks; }

    public MarkStatus getStatus() { return status; }
    public void setStatus(MarkStatus status) { this.status = status; }

    public User getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(User updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
