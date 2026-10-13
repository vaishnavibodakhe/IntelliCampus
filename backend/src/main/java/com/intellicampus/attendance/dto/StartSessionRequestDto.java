package com.intellicampus.attendance.dto;

public class StartSessionRequestDto {
    private Long facultyId;
    private Long subjectId;
    private Long divisionId;
    private Long roomId;
    private String challengeCode;
    private Integer photoWindowMinutes;

    public StartSessionRequestDto() {}

    public Long getFacultyId() { return facultyId; }
    public void setFacultyId(Long facultyId) { this.facultyId = facultyId; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public Long getDivisionId() { return divisionId; }
    public void setDivisionId(Long divisionId) { this.divisionId = divisionId; }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    public String getChallengeCode() { return challengeCode; }
    public void setChallengeCode(String challengeCode) { this.challengeCode = challengeCode; }

    public Integer getPhotoWindowMinutes() { return photoWindowMinutes; }
    public void setPhotoWindowMinutes(Integer photoWindowMinutes) { this.photoWindowMinutes = photoWindowMinutes; }
}
