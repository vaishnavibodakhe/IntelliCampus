package com.intellicampus.ai.client;

import com.intellicampus.ai.dto.RiskResponseDto;
import com.intellicampus.ai.dto.StudentFeatureRowDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    public AiServiceClient() {
        this.restTemplate = new RestTemplate();
    }

    public AiServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
    }

    public List<RiskResponseDto> evaluateStudents(List<StudentFeatureRowDto> students) {
        if (students == null || students.isEmpty()) {
            return Collections.emptyList();
        }

        String targetUrl = aiServiceUrl + "/ai/risk";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<StudentFeatureRowDto>> request = new HttpEntity<>(students, headers);

            ResponseEntity<List<RiskResponseDto>> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<List<RiskResponseDto>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception ex) {
            log.warn("FastAPI microservice at {} is unreachable ({}); applying resilient in-memory risk pipeline.", targetUrl, ex.getMessage());
        }

        // Resilient fallback logic matching ai-service/app/main.py
        return evaluateLocally(students);
    }

    public RiskResponseDto evaluateSingleStudent(StudentFeatureRowDto student) {
        List<RiskResponseDto> results = evaluateStudents(Collections.singletonList(student));
        return results.isEmpty() ? null : results.get(0);
    }

    private List<RiskResponseDto> evaluateLocally(List<StudentFeatureRowDto> students) {
        List<RiskResponseDto> list = new ArrayList<>();
        for (StudentFeatureRowDto s : students) {
            double attRate = s.getAttendanceRate() != null ? s.getAttendanceRate() : 85.0;
            double attDelta = s.getAttendanceDelta4w() != null ? s.getAttendanceDelta4w() : 0.0;
            double marksRate = s.getInternalMarksRate() != null ? s.getInternalMarksRate() : 70.0;
            int backlogs = s.getBacklogs() != null ? s.getBacklogs() : 0;
            int leaveDays = s.getLeaveDaysLast30d() != null ? s.getLeaveDaysLast30d() : 0;

            double attPenalty = Math.max(0.0, (75.0 - attRate) / 75.0) * 0.4;
            double dropPenalty = Math.max(0.0, -attDelta / 50.0) * 0.25;
            double marksPenalty = Math.max(0.0, (50.0 - marksRate) / 50.0) * 0.2;
            double backlogPenalty = Math.min(0.3, backlogs * 0.1);
            double leavePenalty = leaveDays > 4 ? 0.15 : 0.0;

            double rawScore = attPenalty + dropPenalty + marksPenalty + backlogPenalty + leavePenalty;
            double riskScore = Math.min(0.99, Math.max(0.05, Math.round(rawScore * 100.0) / 100.0));

            String level;
            if (riskScore > 0.66) {
                level = "HIGH";
            } else if (riskScore > 0.33) {
                level = "MEDIUM";
            } else {
                level = "LOW";
            }

            List<String> factors = new ArrayList<>();
            if (attRate < 75.0) {
                factors.add(String.format("Attendance rate below 75%% threshold (%.1f%%)", attRate));
            }
            if (attDelta < -10.0) {
                factors.add(String.format("Attendance dropped %.1f points in past 4 weeks", Math.abs(attDelta)));
            }
            if (marksRate < 45.0) {
                factors.add(String.format("Internal evaluation score low (%.1f%%)", marksRate));
            }
            if (backlogs > 0) {
                factors.add("Active backlog count: " + backlogs);
            }
            if (leaveDays > 4) {
                factors.add("High leave frequency: " + leaveDays + " days absent");
            }

            if (factors.isEmpty()) {
                factors.add("Consistent academic standing and regular attendance");
            }

            List<String> topFactors = factors.subList(0, Math.min(3, factors.size()));
            list.add(new RiskResponseDto(s.getStudentId(), riskScore, level, topFactors));
        }
        return list;
    }
}
