package com.intellicampus.ai.dto;

import java.util.ArrayList;
import java.util.List;

public class RiskResponseDto {
    private Long studentId;
    private Double riskScore;
    private String level; // LOW, MEDIUM, HIGH
    private List<String> topFactors = new ArrayList<>();

    public RiskResponseDto() {}

    public RiskResponseDto(Long studentId, Double riskScore, String level, List<String> topFactors) {
        this.studentId = studentId;
        this.riskScore = riskScore;
        this.level = level;
        this.topFactors = topFactors != null ? topFactors : new ArrayList<>();
    }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public List<String> getTopFactors() { return topFactors; }
    public void setTopFactors(List<String> topFactors) { this.topFactors = topFactors; }
}
