package com.intellicampus.leave.dto;

import com.intellicampus.leave.model.LeaveStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveResponseDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private String rollNo;
    private String departmentName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer daysCount;
    private String reason;
    private String attachmentName;
    private String attachmentUrl;
    private Long mentorId;
    private String mentorName;
    private LeaveStatus mentorStatus;
    private String mentorComment;
    private Long hodId;
    private String hodName;
    private LeaveStatus hodStatus;
    private String hodComment;
    private LeaveStatus finalStatus;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;

    public LeaveResponseDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

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

    public Long getMentorId() { return mentorId; }
    public void setMentorId(Long mentorId) { this.mentorId = mentorId; }

    public String getMentorName() { return mentorName; }
    public void setMentorName(String mentorName) { this.mentorName = mentorName; }

    public LeaveStatus getMentorStatus() { return mentorStatus; }
    public void setMentorStatus(LeaveStatus mentorStatus) { this.mentorStatus = mentorStatus; }

    public String getMentorComment() { return mentorComment; }
    public void setMentorComment(String mentorComment) { this.mentorComment = mentorComment; }

    public Long getHodId() { return hodId; }
    public void setHodId(Long hodId) { this.hodId = hodId; }

    public String getHodName() { return hodName; }
    public void setHodName(String hodName) { this.hodName = hodName; }

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
