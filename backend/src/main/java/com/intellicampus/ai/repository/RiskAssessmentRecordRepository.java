package com.intellicampus.ai.repository;

import com.intellicampus.ai.model.RiskAssessmentRecord;
import com.intellicampus.ai.model.RiskLevel;
import com.intellicampus.user.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskAssessmentRecordRepository extends JpaRepository<RiskAssessmentRecord, Long> {
    List<RiskAssessmentRecord> findByStudentOrderByEvaluatedAtDesc(StudentProfile student);
    Optional<RiskAssessmentRecord> findFirstByStudentOrderByEvaluatedAtDesc(StudentProfile student);
    List<RiskAssessmentRecord> findByLevelOrderByRiskScoreDesc(RiskLevel level);
}
