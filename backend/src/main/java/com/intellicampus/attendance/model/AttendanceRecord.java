package com.intellicampus.attendance.model;

import com.intellicampus.user.model.StudentProfile;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records", uniqueConstraints = {
    @UniqueConstraint(name = "uq_session_student", columnNames = {"session_id", "student_id"})
}, indexes = {
    @Index(name = "idx_records_status", columnList = "final_status"),
    @Index(name = "idx_records_student", columnList = "student_id"),
    @Index(name = "idx_records_session", columnList = "session_id")
})
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "face_score")
    private Double faceScore;

    @Column(name = "spoof_score")
    private Double spoofScore;

    @Column(nullable = false)
    private Boolean confirmed = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirm_lat")
    private Double confirmLat;

    @Column(name = "confirm_lng")
    private Double confirmLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_status", nullable = false, length = 16)
    private AttendanceStatus finalStatus = AttendanceStatus.PENDING;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public AttendanceRecord() {}

    public AttendanceRecord(AttendanceSession session, StudentProfile student, Double faceScore, Double spoofScore, AttendanceStatus finalStatus) {
        this.session = session;
        this.student = student;
        this.faceScore = faceScore;
        this.spoofScore = spoofScore;
        this.confirmed = false;
        this.finalStatus = finalStatus != null ? finalStatus : AttendanceStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AttendanceSession getSession() { return session; }
    public void setSession(AttendanceSession session) { this.session = session; }

    public StudentProfile getStudent() { return student; }
    public void setStudent(StudentProfile student) { this.student = student; }

    public Double getFaceScore() { return faceScore; }
    public void setFaceScore(Double faceScore) { this.faceScore = faceScore; }

    public Double getSpoofScore() { return spoofScore; }
    public void setSpoofScore(Double spoofScore) { this.spoofScore = spoofScore; }

    public Boolean getConfirmed() { return confirmed; }
    public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public Double getConfirmLat() { return confirmLat; }
    public void setConfirmLat(Double confirmLat) { this.confirmLat = confirmLat; }

    public Double getConfirmLng() { return confirmLng; }
    public void setConfirmLng(Double confirmLng) { this.confirmLng = confirmLng; }

    public AttendanceStatus getFinalStatus() { return finalStatus; }
    public void setFinalStatus(AttendanceStatus finalStatus) { this.finalStatus = finalStatus; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
