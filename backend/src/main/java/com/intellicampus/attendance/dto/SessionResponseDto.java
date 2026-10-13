package com.intellicampus.attendance.dto;

import com.intellicampus.attendance.model.SessionStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SessionResponseDto {
    private Long id;
    private Long facultyId;
    private String facultyName;
    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    private Long roomId;
    private String roomName;
    private Long divisionId;
    private String divisionName;
    private String challengeCode;
    private SessionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime photoWindowEndsAt;
    private LocalDateTime finalisedAt;
    private long totalPresent;
    private long totalMatched;
    private long totalReview;
    private long totalAbsent;
    private List<AttendanceRecordResponseDto> records = new ArrayList<>();

    public SessionResponseDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFacultyId() { return facultyId; }
    public void setFacultyId(Long facultyId) { this.facultyId = facultyId; }

    public String getFacultyName() { return facultyName; }
    public void setFacultyName(String facultyName) { this.facultyName = facultyName; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public Long getDivisionId() { return divisionId; }
    public void setDivisionId(Long divisionId) { this.divisionId = divisionId; }

    public String getDivisionName() { return divisionName; }
    public void setDivisionName(String divisionName) { this.divisionName = divisionName; }

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

    public long getTotalPresent() { return totalPresent; }
    public void setTotalPresent(long totalPresent) { this.totalPresent = totalPresent; }

    public long getTotalMatched() { return totalMatched; }
    public void setTotalMatched(long totalMatched) { this.totalMatched = totalMatched; }

    public long getTotalReview() { return totalReview; }
    public void setTotalReview(long totalReview) { this.totalReview = totalReview; }

    public long getTotalAbsent() { return totalAbsent; }
    public void setTotalAbsent(long totalAbsent) { this.totalAbsent = totalAbsent; }

    public List<AttendanceRecordResponseDto> getRecords() { return records; }
    public void setRecords(List<AttendanceRecordResponseDto> records) { this.records = records; }
}
