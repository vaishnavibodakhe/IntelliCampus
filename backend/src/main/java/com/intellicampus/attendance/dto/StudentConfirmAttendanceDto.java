package com.intellicampus.attendance.dto;

public class StudentConfirmAttendanceDto {
    private Long studentId;
    private String qrToken;
    private String challengeCode;
    private Double latitude;
    private Double longitude;
    private Double accuracyM;

    public StudentConfirmAttendanceDto() {}

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }

    public String getChallengeCode() { return challengeCode; }
    public void setChallengeCode(String challengeCode) { this.challengeCode = challengeCode; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getAccuracyM() { return accuracyM; }
    public void setAccuracyM(Double accuracyM) { this.accuracyM = accuracyM; }
}
