package com.intellicampus.attendance.dto;

import java.util.List;

public class DetectedFaceDto {
    private int faceId;
    private List<Integer> bbox;
    private Double quality;
    private Long matchedStudentId;
    private String studentName;
    private Double similarity;
    private Double spoofScore;
    private String status;

    public DetectedFaceDto() {}

    public DetectedFaceDto(int faceId, List<Integer> bbox, Double quality, Long matchedStudentId, String studentName, Double similarity, Double spoofScore, String status) {
        this.faceId = faceId;
        this.bbox = bbox;
        this.quality = quality;
        this.matchedStudentId = matchedStudentId;
        this.studentName = studentName;
        this.similarity = similarity;
        this.spoofScore = spoofScore;
        this.status = status;
    }

    public int getFaceId() { return faceId; }
    public void setFaceId(int faceId) { this.faceId = faceId; }

    public List<Integer> getBbox() { return bbox; }
    public void setBbox(List<Integer> bbox) { this.bbox = bbox; }

    public Double getQuality() { return quality; }
    public void setQuality(Double quality) { this.quality = quality; }

    public Long getMatchedStudentId() { return matchedStudentId; }
    public void setMatchedStudentId(Long matchedStudentId) { this.matchedStudentId = matchedStudentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public Double getSimilarity() { return similarity; }
    public void setSimilarity(Double similarity) { this.similarity = similarity; }

    public Double getSpoofScore() { return spoofScore; }
    public void setSpoofScore(Double spoofScore) { this.spoofScore = spoofScore; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
