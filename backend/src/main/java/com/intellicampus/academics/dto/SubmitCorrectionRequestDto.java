package com.intellicampus.academics.dto;

public class SubmitCorrectionRequestDto {
    private Long markId;
    private Double newMarks;
    private String reason;
    private Long facultyId;

    public SubmitCorrectionRequestDto() {}

    public Long getMarkId() { return markId; }
    public void setMarkId(Long markId) { this.markId = markId; }

    public Double getNewMarks() { return newMarks; }
    public void setNewMarks(Double newMarks) { this.newMarks = newMarks; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getFacultyId() { return facultyId; }
    public void setFacultyId(Long facultyId) { this.facultyId = facultyId; }
}
