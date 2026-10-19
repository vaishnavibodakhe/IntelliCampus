package com.intellicampus.user.controller;

import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.FaceEnrollmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/{studentId}/face-enroll", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> enrollFace(
            @PathVariable Long studentId,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(value = "consentVersion", required = false, defaultValue = "1.0") String consentVersion) {

        StudentProfile profile = faceEnrollmentService.enrollStudentFace(studentId, photo, consentVersion);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("studentId", profile.getId());
        response.put("rollNo", profile.getRollNo());
        response.put("faceEnrolled", profile.getFaceEnrolled());
        response.put("consentAt", profile.getConsentAt());
        response.put("consentVersion", profile.getConsentVersion());
        response.put("message", "Biometric face template enrolled successfully.");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{studentId}/face-photo")
    public ResponseEntity<Resource> getFacePhoto(
            @PathVariable Long studentId,
            @RequestParam(value = "requesterId", required = false) Long requesterId) {

        User requester = null;
        if (requesterId != null) {
            requester = userRepository.findById(requesterId).orElse(null);
        }

        Resource resource = faceEnrollmentService.loadStudentFacePhoto(studentId, requester);
        String filename = resource.getFilename() != null ? resource.getFilename() : "photo.jpg";
        MediaType mediaType = filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
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