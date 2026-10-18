package com.intellicampus.ai.model;

import com.intellicampus.user.model.StudentProfile;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_assessments", indexes = {
    @Index(name = "idx_risk_student", columnList = "student_id"),
    @Index(name = "idx_risk_level", columnList = "level"),
    @Index(name = "idx_risk_evaluated_at", columnList = "evaluated_at")
})
public class RiskAssessmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private RiskLevel level;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private RiskTrend trend = RiskTrend.STABLE;

    @Column(name = "top_factors", columnDefinition = "TEXT")
    private String topFactors;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt = LocalDateTime.now();

    public RiskAssessmentRecord() {}

    public RiskAssessmentRecord(StudentProfile student, Double riskScore, RiskLevel level, RiskTrend trend, String topFactors) {
        this.student = student;
        this.riskScore = riskScore;
        this.level = level;
        this.trend = trend != null ? trend : RiskTrend.STABLE;
        this.topFactors = topFactors;
        this.evaluatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StudentProfile getStudent() { return student; }
    public void setStudent(StudentProfile student) { this.student = student; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public RiskLevel getLevel() { return level; }
    public void setLevel(RiskLevel level) { this.level = level; }

    public RiskTrend getTrend() { return trend; }
    public void setTrend(RiskTrend trend) { this.trend = trend; }

    public String getTopFactors() { return topFactors; }
    public void setTopFactors(String topFactors) { this.topFactors = topFactors; }

    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
