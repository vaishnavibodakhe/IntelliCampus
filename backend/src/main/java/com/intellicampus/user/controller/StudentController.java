package com.intellicampus.user.controller;

import com.intellicampus.user.dto.CaptureTokenResponseDto;
import com.intellicampus.user.dto.FaceEnrollmentRequestDto;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.FaceEnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/students")
@CrossOrigin(origins = "*")
public class StudentController {

    private final FaceEnrollmentService faceEnrollmentService;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;

    public StudentController(
            FaceEnrollmentService faceEnrollmentService,
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository) {
        this.faceEnrollmentService = faceEnrollmentService;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
    }

    /**
     * Issues a one-time capture token bound to pending student account and client IP (5 min TTL).
     */
    @PostMapping("/{studentId}/capture-token")
    public ResponseEntity<CaptureTokenResponseDto> issueCaptureToken(
            @PathVariable Long studentId,
            HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        CaptureTokenResponseDto tokenDto = faceEnrollmentService.issueCaptureToken(studentId, clientIp);
        return ResponseEntity.ok(tokenDto);
    }

    /**
     * Completes biometric face enrollment using 3 live camera frames (front, left, right),
     * one-time capture token, and liveness result.
     */
    @PostMapping("/{studentId}/face-enroll")
    public ResponseEntity<Map<String, Object>> enrollFace(
            @PathVariable Long studentId,
            @RequestBody FaceEnrollmentRequestDto req,
            HttpServletRequest request) {

        String clientIp = extractClientIp(request);
        StudentProfile profile = faceEnrollmentService.enrollStudentFace(studentId, req, clientIp);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("studentId", profile.getId());
        response.put("rollNo", profile.getRollNo());
        response.put("faceEnrolled", profile.getFaceEnrolled());
        response.put("consentAt", profile.getConsentAt());
        response.put("consentVersion", profile.getConsentVersion());
        response.put("message", "Biometric face template enrolled successfully from live camera.");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<Map<String, Object>> getStudent(@PathVariable Long studentId) {
        StudentProfile p = faceEnrollmentService.findStudent(studentId);
        return ResponseEntity.ok(mapStudentToSafeDto(p));
    }

    @GetMapping("/division/{divisionId}")
    public ResponseEntity<List<Map<String, Object>>> getStudentsByDivision(@PathVariable Long divisionId) {
        List<StudentProfile> students = studentProfileRepository.findByDivisionId(divisionId);
        List<Map<String, Object>> result = students.stream()
                .map(this::mapStudentToSafeDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }

    private Map<String, Object> mapStudentToSafeDto(StudentProfile p) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", p.getId());
        dto.put("userId", p.getUser() != null ? p.getUser().getId() : null);
        dto.put("name", p.getUser() != null ? p.getUser().getName() : "");
        dto.put("email", p.getUser() != null ? p.getUser().getEmail() : "");
        dto.put("userCode", p.getUser() != null ? p.getUser().getUserCode() : "");
        dto.put("rollNo", p.getRollNo());
        dto.put("divisionId", p.getDivision() != null ? p.getDivision().getId() : null);
        dto.put("divisionName", p.getDivision() != null ? p.getDivision().getName() : "");
        dto.put("faceEnrolled", Boolean.TRUE.equals(p.getFaceEnrolled()));
        dto.put("consentAt", p.getConsentAt());
        dto.put("consentVersion", p.getConsentVersion());
        // Crucial security requirement: NEVER expose raw embedding or internal photo path
        return dto;
    }
}