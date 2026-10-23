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
    private int unmatchedFaces;
    private List<AttendanceRecordResponseDto> records;
    private List<DetectedFaceDto> detectedFaces;
    private Integer imageWidth;
    private Integer imageHeight;
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

    public int getUnmatchedFaceCount() { return unmatchedFaceCount != 0 ? unmatchedFaceCount : unmatchedFaces; }
    public void setUnmatchedFaceCount(int unmatchedFaceCount) {
        this.unmatchedFaceCount = unmatchedFaceCount;
        this.unmatchedFaces = unmatchedFaceCount;
    }

    public int getUnmatchedFaces() { return unmatchedFaces != 0 ? unmatchedFaces : unmatchedFaceCount; }
    public void setUnmatchedFaces(int unmatchedFaces) {
        this.unmatchedFaces = unmatchedFaces;
        this.unmatchedFaceCount = unmatchedFaces;
    }

    public List<AttendanceRecordResponseDto> getRecords() { return records; }
    public void setRecords(List<AttendanceRecordResponseDto> records) { this.records = records; }

    public List<DetectedFaceDto> getDetectedFaces() { return detectedFaces; }
    public void setDetectedFaces(List<DetectedFaceDto> detectedFaces) { this.detectedFaces = detectedFaces; }

    public Integer getImageWidth() { return imageWidth; }
    public void setImageWidth(Integer imageWidth) { this.imageWidth = imageWidth; }

    public Integer getImageHeight() { return imageHeight; }
    public void setImageHeight(Integer imageHeight) { this.imageHeight = imageHeight; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}