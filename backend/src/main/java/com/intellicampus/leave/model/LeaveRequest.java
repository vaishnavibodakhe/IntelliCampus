package com.intellicampus.leave.model;

import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests", indexes = {
    @Index(name = "idx_leaves_student", columnList = "student_id"),
    @Index(name = "idx_leaves_final_status", columnList = "final_status"),
    @Index(name = "idx_leaves_from_date", columnList = "from_date")
})
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "days_count", nullable = false)
    private Integer daysCount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "attachment_name", length = 200)
    private String attachmentName;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id")
    private User mentor;

    @Enumerated(EnumType.STRING)
    @Column(name = "mentor_status", nullable = false, length = 20)
    private LeaveStatus mentorStatus = LeaveStatus.SUBMITTED;

    @Column(name = "mentor_comment", length = 500)
    private String mentorComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hod_id")
    private User hod;

    @Enumerated(EnumType.STRING)
    @Column(name = "hod_status", nullable = false, length = 20)
    private LeaveStatus hodStatus = LeaveStatus.SUBMITTED;

    @Column(name = "hod_comment", length = 500)
    private String hodComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_status", nullable = false, length = 20)
    private LeaveStatus finalStatus = LeaveStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public LeaveRequest() {}

    public LeaveRequest(StudentProfile student, LocalDate fromDate, LocalDate toDate, Integer daysCount, String reason, String attachmentName, String attachmentUrl, User mentor) {
        this.student = student;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.daysCount = daysCount != null ? daysCount : 1;
        this.reason = reason;
        this.attachmentName = attachmentName;
        this.attachmentUrl = attachmentUrl;
        this.mentor = mentor;
        this.mentorStatus = LeaveStatus.SUBMITTED;
        this.hodStatus = LeaveStatus.SUBMITTED;
        this.finalStatus = LeaveStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StudentProfile getStudent() { return student; }
    public void setStudent(StudentProfile student) { this.student = student; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public Integer getDaysCount() { return daysCount; }
    public void setDaysCount(Integer daysCount) { this.daysCount = daysCount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public User getMentor() { return mentor; }
    public void setMentor(User mentor) { this.mentor = mentor; }

    public LeaveStatus getMentorStatus() { return mentorStatus; }
    public void setMentorStatus(LeaveStatus mentorStatus) { this.mentorStatus = mentorStatus; }

    public String getMentorComment() { return mentorComment; }
    public void setMentorComment(String mentorComment) { this.mentorComment = mentorComment; }

    public User getHod() { return hod; }
    public void setHod(User hod) { this.hod = hod; }

    public LeaveStatus getHodStatus() { return hodStatus; }
    public void setHodStatus(LeaveStatus hodStatus) { this.hodStatus = hodStatus; }

    public String getHodComment() { return hodComment; }
    public void setHodComment(String hodComment) { this.hodComment = hodComment; }

    public LeaveStatus getFinalStatus() { return finalStatus; }
    public void setFinalStatus(LeaveStatus finalStatus) { this.finalStatus = finalStatus; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}
