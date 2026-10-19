package com.intellicampus.attendance.dto;

import java.util.List;

public class PhotoRecognitionResultDto {
    private Long sessionId;
    private Long photoId;
    private int photoIndex;
    private int totalPhotos;
    private int facesDetected;
    private int studentsMatched;
    private int studentsInReview;
    private int unmatchedFaceCount;
    private List<AttendanceRecordResponseDto> records;
    private String message;

    public PhotoRecognitionResultDto() {}

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getPhotoId() { return photoId; }
    public void setPhotoId(Long photoId) { this.photoId = photoId; }

    public int getPhotoIndex() { return photoIndex; }
    public void setPhotoIndex(int photoIndex) { this.photoIndex = photoIndex; }

    public int getTotalPhotos() { return totalPhotos; }
    public void setTotalPhotos(int totalPhotos) { this.totalPhotos = totalPhotos; }

    public int getFacesDetected() { return facesDetected; }
    public void setFacesDetected(int facesDetected) { this.facesDetected = facesDetected; }

    public int getStudentsMatched() { return studentsMatched; }
    public void setStudentsMatched(int studentsMatched) { this.studentsMatched = studentsMatched; }

    public int getStudentsInReview() { return studentsInReview; }
    public void setStudentsInReview(int studentsInReview) { this.studentsInReview = studentsInReview; }

    public int getUnmatchedFaceCount() { return unmatchedFaceCount; }
    public void setUnmatchedFaceCount(int unmatchedFaceCount) { this.unmatchedFaceCount = unmatchedFaceCount; }

    public List<AttendanceRecordResponseDto> getRecords() { return records; }
    public void setRecords(List<AttendanceRecordResponseDto> records) { this.records = records; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}