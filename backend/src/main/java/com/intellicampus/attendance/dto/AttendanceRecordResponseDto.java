package com.intellicampus.attendance.dto;

import com.intellicampus.attendance.model.AttendanceStatus;
import java.time.LocalDateTime;

public class AttendanceRecordResponseDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private String rollNo;
    private Double faceScore;
    private Double spoofScore;
    private Boolean confirmed;
    private LocalDateTime confirmedAt;
    private AttendanceStatus finalStatus;
    private String statusReason;

    public AttendanceRecordResponseDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public Double getFaceScore() { return faceScore; }
    public void setFaceScore(Double faceScore) { this.faceScore = faceScore; }

    public Double getSpoofScore() { return spoofScore; }
    public void setSpoofScore(Double spoofScore) { this.spoofScore = spoofScore; }

    public Boolean getConfirmed() { return confirmed; }
    public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public AttendanceStatus getFinalStatus() { return finalStatus; }
    public void setFinalStatus(AttendanceStatus finalStatus) { this.finalStatus = finalStatus; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
}
