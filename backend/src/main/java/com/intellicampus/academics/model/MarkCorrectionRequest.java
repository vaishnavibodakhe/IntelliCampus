package com.intellicampus.academics.model;

import com.intellicampus.user.model.User;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mark_correction_requests", indexes = {
    @Index(name = "idx_corrections_status", columnList = "status"),
    @Index(name = "idx_corrections_mark", columnList = "mark_id")
})
public class MarkCorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mark_id", nullable = false)
    private MarkEntry markEntry;

    @Column(name = "old_marks", nullable = false)
    private Double oldMarks;

    @Column(name = "new_marks", nullable = false)
    private Double newMarks;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private User faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hod_id")
    private User hod;

    @Column(name = "decision_comment", length = 500)
    private String decisionComment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private CorrectionStatus status = CorrectionStatus.PENDING_HOD;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public MarkCorrectionRequest() {}

    public MarkCorrectionRequest(MarkEntry markEntry, Double oldMarks, Double newMarks, String reason, User faculty) {
        this.markEntry = markEntry;
        this.oldMarks = oldMarks;
        this.newMarks = newMarks;
        this.reason = reason;
        this.faculty = faculty;
        this.status = CorrectionStatus.PENDING_HOD;
        this.requestedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MarkEntry getMarkEntry() { return markEntry; }
    public void setMarkEntry(MarkEntry markEntry) { this.markEntry = markEntry; }

    public Double getOldMarks() { return oldMarks; }
    public void setOldMarks(Double oldMarks) { this.oldMarks = oldMarks; }

    public Double getNewMarks() { return newMarks; }
    public void setNewMarks(Double newMarks) { this.newMarks = newMarks; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public User getFaculty() { return faculty; }
    public void setFaculty(User faculty) { this.faculty = faculty; }

    public User getHod() { return hod; }
    public void setHod(User hod) { this.hod = hod; }

    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }

    public CorrectionStatus getStatus() { return status; }
    public void setStatus(CorrectionStatus status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}
