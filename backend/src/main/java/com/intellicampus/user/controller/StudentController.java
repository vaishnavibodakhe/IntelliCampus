package com.intellicampus.user.controller;

import com.intellicampus.user.dto.CaptureTokenResponseDto;
import com.intellicampus.user.dto.FaceEnrollmentRequestDto;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.FaceEnrollmentService;
import com.intellicampus.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public ResponseEntity<Map<String, Object>> getStudent(
            @PathVariable Long studentId,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        StudentProfile p = faceEnrollmentService.findStudent(studentId);

        boolean isStaff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN")
                               || role.equals("ROLE_HOD")
                               || role.equals("ROLE_FACULTY")
                               || role.equals("ROLE_MENTOR"));

        if (!isStaff) {
            if (!isStudentOwnRecord(p, authentication)) {
                throw new AccessDeniedException("Access is denied: students may only access their own record");
            }
        }

        return ResponseEntity.ok(mapStudentToSafeDto(p));
    }

    @GetMapping("/division/{divisionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<Map<String, Object>>> getStudentsByDivision(@PathVariable Long divisionId) {
        List<StudentProfile> students = studentProfileRepository.findByDivisionId(divisionId);
        List<Map<String, Object>> result = students.stream()
                .map(this::mapStudentToSafeDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private boolean isStudentOwnRecord(StudentProfile p, Authentication authentication) {
        if (p == null || authentication == null) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            if (p.getUser() != null && user.getId() != null && user.getId().equals(p.getUser().getId())) {
                return true;
            }
            if (p.getUser() != null && user.getEmail() != null && user.getEmail().equalsIgnoreCase(p.getUser().getEmail())) {
                return true;
            }
        }

        String authName = authentication.getName();
        if (authName != null && !authName.isBlank()) {
            if (p.getUser() != null) {
                if (authName.equalsIgnoreCase(p.getUser().getEmail())) {
                    return true;
                }
                if (authName.equalsIgnoreCase(p.getUser().getUserCode())) {
                    return true;
                }
            }
            if (authName.equalsIgnoreCase(p.getRollNo())) {
                return true;
            }
            if (p.getId() != null && authName.equals(String.valueOf(p.getId()))) {
                return true;
            }
            if (p.getUser() != null && p.getUser().getId() != null && authName.equals(String.valueOf(p.getUser().getId()))) {
                return true;
            }

            Optional<User> authUserOpt = userRepository.findByEmail(authName)
                    .or(() -> userRepository.findByUserCode(authName));
            if (authUserOpt.isPresent()) {
                User authUser = authUserOpt.get();
                if (p.getUser() != null && authUser.getId().equals(p.getUser().getId())) {
                    return true;
                }
            }
        }

        return false;
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