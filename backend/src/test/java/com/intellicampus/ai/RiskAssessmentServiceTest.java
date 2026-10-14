package com.intellicampus.ai;

import com.intellicampus.ai.client.AiServiceClient;
import com.intellicampus.ai.dto.RiskResponseDto;
import com.intellicampus.ai.dto.StudentFeatureRowDto;
import com.intellicampus.ai.model.RiskAssessmentRecord;
import com.intellicampus.ai.model.RiskLevel;
import com.intellicampus.ai.repository.RiskAssessmentRecordRepository;
import com.intellicampus.ai.service.RiskAssessmentService;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for RiskAssessmentService verifying FastAPI student risk payload parsing
 * and explainability factor extraction.
 * Compliant with IC-SRS-1.0 Section 8.8 and Test Case TC-08.
 */
@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    @Mock
    private RiskAssessmentRecordRepository riskRepository;
    @Mock
    private StudentProfileRepository studentRepository;
    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private RiskAssessmentService riskAssessmentService;

    private User studentUser;
    private StudentProfile studentProfile;

    @BeforeEach
    void setUp() {
        studentUser = new User("STU-301", Role.STUDENT, "Aditya Rao", "aditya@intellicampus.edu", "pass", null);
        ReflectionTestUtils.setField(studentUser, "id", 1L);

        studentProfile = new StudentProfile(studentUser, "2024-CS-077");
        studentProfile.setAttendanceRate(62.5);
        studentProfile.setBacklogs(2);
        ReflectionTestUtils.setField(studentProfile, "id", 10L);
    }

    @Nested
    @DisplayName("FastAPI Payload Parsing & Explainability Factor Extraction Tests")
    class RiskPayloadParsingTests {

        @Test
        @DisplayName("Criterion 3: Parses FastAPI risk response and extracts top explainability factors into persisted record")
        void testEvaluateAndSaveStudentRiskPayloadParsing() {
            RiskResponseDto fastApiResponse = new RiskResponseDto(
                    10L,
                    0.84,
                    "HIGH",
                    List.of(
                            "Attendance rate below 75% threshold (62.5%)",
                            "Active backlog count: 2",
                            "Attendance dropped 14.0 points in past 4 weeks"
                    )
            );

            when(studentRepository.findById(10L)).thenReturn(Optional.of(studentProfile));
            when(aiServiceClient.evaluateSingleStudent(any(StudentFeatureRowDto.class))).thenReturn(fastApiResponse);
            when(riskRepository.save(any(RiskAssessmentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            RiskResponseDto result = riskAssessmentService.evaluateAndSaveStudentRisk(10L);

            assertNotNull(result);
            assertEquals(0.84, result.getRiskScore());
            assertEquals("HIGH", result.getLevel());
            assertEquals(3, result.getTopFactors().size(), "Should extract 3 explainability factors");
            assertEquals("Attendance rate below 75% threshold (62.5%)", result.getTopFactors().get(0));

            verify(riskRepository).save(argThat(record -> {
                assertEquals(RiskLevel.HIGH, record.getLevel());
                assertEquals(0.84, record.getRiskScore());
                assertTrue(record.getTopFactors().contains("Attendance rate below 75%"));
                assertTrue(record.getTopFactors().contains("Active backlog count: 2"));
                return true;
            }));
        }

        @Test
        @DisplayName("Local fallback pipeline evaluates risk and generates explainability factors when microservice is offline")
        void testLocalFallbackEvaluation() {
            AiServiceClient clientWithFallback = new AiServiceClient();
            StudentFeatureRowDto row = new StudentFeatureRowDto(
                    10L,
                    64.0,   // Below 75% threshold
                    -12.0,  // Drop > 10 points
                    40.0,   // Internal marks low
                    0.0,
                    2,      // Backlogs > 0
                    5       // Leave days > 4
            );

            RiskResponseDto fallbackResult = clientWithFallback.evaluateSingleStudent(row);

            assertNotNull(fallbackResult);
            assertTrue(fallbackResult.getRiskScore() > 0.50, "Risk score should be elevated");
            assertNotNull(fallbackResult.getLevel());
            assertFalse(fallbackResult.getTopFactors().isEmpty(), "Top explainability factors must be populated");
            assertTrue(fallbackResult.getTopFactors().stream().anyMatch(f -> f.contains("Attendance rate below 75%")));
        }
    }
}
