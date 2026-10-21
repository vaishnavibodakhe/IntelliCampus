package com.intellicampus.ai.controller;

import com.intellicampus.ai.client.AiServiceClient;
import com.intellicampus.ai.dto.RiskResponseDto;
import com.intellicampus.ai.dto.StudentFeatureRowDto;
import com.intellicampus.ai.model.RiskAssessmentRecord;
import com.intellicampus.ai.service.RiskAssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/risk")
@CrossOrigin(origins = "*")
public class RiskAssessmentController {

    private final RiskAssessmentService riskService;
    private final AiServiceClient aiServiceClient;

    public RiskAssessmentController(RiskAssessmentService riskService, AiServiceClient aiServiceClient) {
        this.riskService = riskService;
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<RiskResponseDto>> evaluateBatch(@RequestBody List<StudentFeatureRowDto> students) {
        return ResponseEntity.ok(aiServiceClient.evaluateStudents(students));
    }

    @PostMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<RiskResponseDto> evaluateStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(riskService.evaluateAndSaveStudentRisk(studentId));
    }

    @GetMapping("/student/{studentId}/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<?> getLatestStudentRisk(@PathVariable Long studentId) {
        return riskService.getLatestRiskForStudent(studentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/high")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<RiskAssessmentRecord>> getHighRiskStudents() {
        return ResponseEntity.ok(riskService.getHighRiskStudents());
    }
}
