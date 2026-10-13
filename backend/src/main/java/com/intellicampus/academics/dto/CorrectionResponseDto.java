package com.intellicampus.academics.dto;

import com.intellicampus.academics.model.CorrectionStatus;
import java.time.LocalDateTime;

public class CorrectionResponseDto {
    private Long id;
    private Long markId;
    private String studentName;
    private String rollNo;
    private String subjectCode;
    private String exam;
    private Double oldMarks;
    private Double newMarks;
    private String reason;
    private String facultyName;
    private CorrectionStatus status;
    private LocalDateTime requestedAt;
    private String decisionComment;

    public CorrectionResponseDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMarkId() { return markId; }
    public void setMarkId(Long markId) { this.markId = markId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getExam() { return exam; }
    public void setExam(String exam) { this.exam = exam; }

    public Double getOldMarks() { return oldMarks; }
    public void setOldMarks(Double oldMarks) { this.oldMarks = oldMarks; }

    public Double getNewMarks() { return newMarks; }
    public void setNewMarks(Double newMarks) { this.newMarks = newMarks; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getFacultyName() { return facultyName; }
    public void setFacultyName(String facultyName) { this.facultyName = facultyName; }

    public CorrectionStatus getStatus() { return status; }
    public void setStatus(CorrectionStatus status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
}
