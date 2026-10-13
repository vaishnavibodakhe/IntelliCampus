package com.intellicampus.ai.service;

import com.intellicampus.ai.client.AiServiceClient;
import com.intellicampus.ai.dto.RiskResponseDto;
import com.intellicampus.ai.dto.StudentFeatureRowDto;
import com.intellicampus.ai.model.RiskAssessmentRecord;
import com.intellicampus.ai.model.RiskLevel;
import com.intellicampus.ai.model.RiskTrend;
import com.intellicampus.ai.repository.RiskAssessmentRecordRepository;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class RiskAssessmentService {

    private final RiskAssessmentRecordRepository riskRepository;
    private final StudentProfileRepository studentRepository;
    private final AiServiceClient aiServiceClient;

    public RiskAssessmentService(RiskAssessmentRecordRepository riskRepository,
                                 StudentProfileRepository studentRepository,
                                 AiServiceClient aiServiceClient) {
        this.riskRepository = riskRepository;
        this.studentRepository = studentRepository;
        this.aiServiceClient = aiServiceClient;
    }

    public RiskResponseDto evaluateAndSaveStudentRisk(Long studentId) {
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        StudentFeatureRowDto row = new StudentFeatureRowDto(
                student.getId(),
                student.getAttendanceRate(),
                -2.0, // trend delta
                65.0, // default marks rate
                0.0,
                student.getBacklogs(),
                1
        );

        RiskResponseDto response = aiServiceClient.evaluateSingleStudent(row);
        if (response != null) {
            RiskLevel level = RiskLevel.valueOf(response.getLevel());
            String factors = String.join("; ", response.getTopFactors());

            RiskAssessmentRecord record = new RiskAssessmentRecord(
                    student,
                    response.getRiskScore(),
                    level,
                    RiskTrend.STABLE,
                    factors
            );
            riskRepository.save(record);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public Optional<RiskAssessmentRecord> getLatestRiskForStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .flatMap(riskRepository::findFirstByStudentOrderByEvaluatedAtDesc);
    }

    @Transactional(readOnly = true)
    public List<RiskAssessmentRecord> getHighRiskStudents() {
        return riskRepository.findByLevelOrderByRiskScoreDesc(RiskLevel.HIGH);
    }
}
