package com.intellicampus.attendance.model;

import com.intellicampus.academic.model.Classroom;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.model.Subject;
import com.intellicampus.user.model.User;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_sessions", indexes = {
    @Index(name = "idx_sessions_status", columnList = "status"),
    @Index(name = "idx_sessions_faculty", columnList = "faculty_id"),
    @Index(name = "idx_sessions_started_at", columnList = "started_at")
})
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private User faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = false)
    private Division division;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Classroom room;

    @Column(name = "challenge_code", nullable = false, length = 8)
    private String challengeCode;

    @Column(name = "session_nonce", length = 64)
    private String sessionNonce;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private SessionStatus status = SessionStatus.OPEN;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "photo_window_ends_at")
    private LocalDateTime photoWindowEndsAt;

    @Column(name = "photo_count", nullable = false)
    private Integer photoCount = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "finalised_at")
    private LocalDateTime finalisedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public AttendanceSession() {}

    public AttendanceSession(User faculty, Subject subject, Division division, Classroom room, String challengeCode, Integer photoWindowMinutes) {
        this.faculty = faculty;
        this.subject = subject;
        this.division = division;
        this.room = room;
        this.challengeCode = challengeCode;
        this.status = SessionStatus.OPEN;
        this.startedAt = LocalDateTime.now();
        int window = photoWindowMinutes != null ? photoWindowMinutes : 10;
        this.photoWindowEndsAt = this.startedAt.plusMinutes(window);
        this.expiresAt = this.photoWindowEndsAt;
        this.photoCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getFaculty() { return faculty; }
    public void setFaculty(User faculty) { this.faculty = faculty; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }

    public Classroom getRoom() { return room; }
    public void setRoom(Classroom room) { this.room = room; }

    public String getChallengeCode() { return challengeCode; }
    public void setChallengeCode(String challengeCode) { this.challengeCode = challengeCode; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getPhotoWindowEndsAt() { return photoWindowEndsAt; }
    public void setPhotoWindowEndsAt(LocalDateTime photoWindowEndsAt) { this.photoWindowEndsAt = photoWindowEndsAt; }

    public LocalDateTime getFinalisedAt() { return finalisedAt; }
    public void setFinalisedAt(LocalDateTime finalisedAt) { this.finalisedAt = finalisedAt; }

    public String getSessionNonce() { return sessionNonce; }
    public void setSessionNonce(String sessionNonce) { this.sessionNonce = sessionNonce; }

    public Integer getPhotoCount() { return photoCount; }
    public void setPhotoCount(Integer photoCount) { this.photoCount = photoCount; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
