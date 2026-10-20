package com.intellicampus.attendance.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_photos", indexes = {
    @Index(name = "idx_photos_session", columnList = "session_id"),
    @Index(name = "idx_photos_sha256", columnList = "sha256")
})
public class SessionPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "phash")
    private Long phash;

    @Column(name = "spoof_score")
    private Double spoofScore;

    @Column(name = "flagged_for_review", nullable = false)
    private Boolean flaggedForReview = false;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "accuracy_m", nullable = false)
    private Double accuracyM;

    @Column(name = "face_count")
    private Integer faceCount = 0;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt = LocalDateTime.now();

    public SessionPhoto() {}

    public SessionPhoto(AttendanceSession session, String sha256, Long phash, Double latitude, Double longitude, Double accuracyM, Integer faceCount) {
        this.session = session;
        this.sha256 = sha256;
        this.phash = phash;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyM = accuracyM;
        this.faceCount = faceCount != null ? faceCount : 0;
        this.capturedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AttendanceSession getSession() { return session; }
    public void setSession(AttendanceSession session) { this.session = session; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public Long getPhash() { return phash; }
    public void setPhash(Long phash) { this.phash = phash; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getAccuracyM() { return accuracyM; }
    public void setAccuracyM(Double accuracyM) { this.accuracyM = accuracyM; }

    public Integer getFaceCount() { return faceCount; }
    public void setFaceCount(Integer faceCount) { this.faceCount = faceCount; }

    public Double getSpoofScore() { return spoofScore; }
    public void setSpoofScore(Double spoofScore) { this.spoofScore = spoofScore; }

    public Boolean getFlaggedForReview() { return flaggedForReview; }
    public void setFlaggedForReview(Boolean flaggedForReview) { this.flaggedForReview = flaggedForReview; }

    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
}
