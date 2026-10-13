package com.intellicampus.ai.dto;

public class StudentFeatureRowDto {
    private Long studentId;
    private Double attendanceRate;
    private Double attendanceDelta4w;
    private Double internalMarksRate;
    private Double marksDelta;
    private Integer backlogs;
    private Integer leaveDaysLast30d;

    public StudentFeatureRowDto() {
        this.attendanceRate = 85.0;
        this.attendanceDelta4w = 0.0;
        this.internalMarksRate = 70.0;
        this.marksDelta = 0.0;
        this.backlogs = 0;
        this.leaveDaysLast30d = 0;
    }

    public StudentFeatureRowDto(Long studentId, Double attendanceRate, Double attendanceDelta4w, Double internalMarksRate, Double marksDelta, Integer backlogs, Integer leaveDaysLast30d) {
        this.studentId = studentId;
        this.attendanceRate = attendanceRate != null ? attendanceRate : 85.0;
        this.attendanceDelta4w = attendanceDelta4w != null ? attendanceDelta4w : 0.0;
        this.internalMarksRate = internalMarksRate != null ? internalMarksRate : 70.0;
        this.marksDelta = marksDelta != null ? marksDelta : 0.0;
        this.backlogs = backlogs != null ? backlogs : 0;
        this.leaveDaysLast30d = leaveDaysLast30d != null ? leaveDaysLast30d : 0;
    }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Double getAttendanceRate() { return attendanceRate; }
    public void setAttendanceRate(Double attendanceRate) { this.attendanceRate = attendanceRate; }

    public Double getAttendanceDelta4w() { return attendanceDelta4w; }
    public void setAttendanceDelta4w(Double attendanceDelta4w) { this.attendanceDelta4w = attendanceDelta4w; }

    public Double getInternalMarksRate() { return internalMarksRate; }
    public void setInternalMarksRate(Double internalMarksRate) { this.internalMarksRate = internalMarksRate; }

    public Double getMarksDelta() { return marksDelta; }
    public void setMarksDelta(Double marksDelta) { this.marksDelta = marksDelta; }

    public Integer getBacklogs() { return backlogs; }
    public void setBacklogs(Integer backlogs) { this.backlogs = backlogs; }

    public Integer getLeaveDaysLast30d() { return leaveDaysLast30d; }
    public void setLeaveDaysLast30d(Integer leaveDaysLast30d) { this.leaveDaysLast30d = leaveDaysLast30d; }
}
