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
    private final com.intellicampus.security.util.SecurityUtils securityUtils;

    public RiskAssessmentService(RiskAssessmentRecordRepository riskRepository,
                                 StudentProfileRepository studentRepository,
                                 AiServiceClient aiServiceClient) {
        this(riskRepository, studentRepository, aiServiceClient, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RiskAssessmentService(RiskAssessmentRecordRepository riskRepository,
                                 StudentProfileRepository studentRepository,
                                 AiServiceClient aiServiceClient,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false) com.intellicampus.security.util.SecurityUtils securityUtils) {
        this.riskRepository = riskRepository;
        this.studentRepository = studentRepository;
        this.aiServiceClient = aiServiceClient;
        this.securityUtils = securityUtils;
    }

    public RiskResponseDto evaluateAndSaveStudentRisk(Long studentId) {
        StudentProfile student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        if (securityUtils != null) {
            securityUtils.verifyCanAccessStudent(student);
        }

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
                .map(student -> {
                    if (securityUtils != null) {
                        securityUtils.verifyCanAccessStudent(student);
                    }
                    return student;
                })
                .flatMap(riskRepository::findFirstByStudentOrderByEvaluatedAtDesc);
    }

    @Transactional(readOnly = true)
    public List<RiskAssessmentRecord> getHighRiskStudents() {
        return riskRepository.findByLevelOrderByRiskScoreDesc(RiskLevel.HIGH);
    }
}
