package com.intellicampus.user.model;

import com.intellicampus.academic.model.Division;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "students", indexes = {
    @Index(name = "idx_students_roll_no", columnList = "roll_no")
})
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "roll_no", nullable = false, unique = true, length = 30)
    private String rollNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = false)
    private Division division;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id")
    private User mentor;

    @Column(name = "face_enrolled")
    private Boolean faceEnrolled = false;

    @Column(name = "attendance_rate")
    private Double attendanceRate = 0.0;

    @Column(name = "backlogs")
    private Integer backlogs = 0;

    @Column(name = "fee_status", length = 20)
    private String feeStatus = "PAID";

    @Column(name = "due_amount")
    private Double dueAmount = 0.0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "face_photo_path", length = 255)
    private String facePhotoPath;

    @Column(name = "face_embedding", columnDefinition = "JSON")
    private String faceEmbedding;

    @Column(name = "consent_at")
    private LocalDateTime consentAt;

    @Column(name = "consent_version", length = 20)
    private String consentVersion;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public StudentProfile() {}

    public StudentProfile(User user, String rollNo) {
        this(user, rollNo, null, null);
    }

    public StudentProfile(User user, String rollNo, Division division, User mentor) {
        this.user = user;
        this.rollNo = rollNo;
        this.division = division;
        this.mentor = mentor;
        this.faceEnrolled = false;
        this.attendanceRate = 0.0;
        this.backlogs = 0;
        this.feeStatus = "PAID";
        this.dueAmount = 0.0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }

    public User getMentor() { return mentor; }
    public void setMentor(User mentor) { this.mentor = mentor; }

    public Boolean getFaceEnrolled() { return faceEnrolled; }
    public void setFaceEnrolled(Boolean faceEnrolled) { this.faceEnrolled = faceEnrolled; }

    public Double getAttendanceRate() { return attendanceRate; }
    public void setAttendanceRate(Double attendanceRate) { this.attendanceRate = attendanceRate; }

    public Integer getBacklogs() { return backlogs; }
    public void setBacklogs(Integer backlogs) { this.backlogs = backlogs; }

    public String getFeeStatus() { return feeStatus; }
    public void setFeeStatus(String feeStatus) { this.feeStatus = feeStatus; }

    public Double getDueAmount() { return dueAmount; }
    public void setDueAmount(Double dueAmount) { this.dueAmount = dueAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getFacePhotoPath() { return facePhotoPath; }
    public void setFacePhotoPath(String facePhotoPath) { this.facePhotoPath = facePhotoPath; }

    public String getFaceEmbedding() { return faceEmbedding; }
    public void setFaceEmbedding(String faceEmbedding) { this.faceEmbedding = faceEmbedding; }

    public LocalDateTime getConsentAt() { return consentAt; }
    public void setConsentAt(LocalDateTime consentAt) { this.consentAt = consentAt; }

    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

