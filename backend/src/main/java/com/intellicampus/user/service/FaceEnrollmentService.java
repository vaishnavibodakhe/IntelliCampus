package com.intellicampus.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class FaceEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(FaceEnrollmentService.class);
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png"
    );
    private static final long MAX_FILE_SIZE = 15 * 1024 * 1024; // 15 MB

    private final StudentProfileRepository studentProfileRepository;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private final String faceStorageDir;
    private final String aiServiceUrl;

    public FaceEnrollmentService(
            StudentProfileRepository studentProfileRepository,
            @Value("${app.storage.face-dir:./uploads/student-faces}") String faceStorageDir,
            @Value("${app.ai.service-url:http://localhost:8000}") String aiServiceUrl) {
        this.studentProfileRepository = studentProfileRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.faceStorageDir = faceStorageDir;
        this.aiServiceUrl = aiServiceUrl;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StudentProfile enrollStudentFace(Long studentId, MultipartFile photo, String consentVersion) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Face photo file is mandatory for biometric enrollment.");
        }

        validatePhotoFile(photo);

        StudentProfile student = findStudent(studentId);

        // 1. Call AI microservice to extract 512-d ArcFace embedding
        String embeddingJson = callAiServiceEnroll(photo);

        // 2. Save photo to configurable storage directory
        String savedPath = savePhotoToDisk(student.getId(), photo);

        // 3. Update student record
        student.setFacePhotoPath(savedPath);
        student.setFaceEmbedding(embeddingJson);
        student.setFaceEnrolled(true);
        student.setConsentAt(LocalDateTime.now());
        student.setConsentVersion(consentVersion != null && !consentVersion.isBlank() ? consentVersion : "1.0");

        log.info("Student {} face enrolled successfully. Photo saved at: {}", student.getId(), savedPath);
        return studentProfileRepository.save(student);
    }

    public Resource loadStudentFacePhoto(Long studentId, User requester) {
        if (requester != null && requester.getRole() != Role.ADMIN && requester.getRole() != Role.HOD) {
            throw new SecurityException("Unauthorized: Face photo access is restricted to ADMIN and HOD roles.");
        }

        StudentProfile student = findStudent(studentId);
        if (student.getFacePhotoPath() == null || student.getFacePhotoPath().isBlank()) {
            throw new IllegalArgumentException("No face photo enrolled for student " + studentId);
        }

        Path path = Paths.get(student.getFacePhotoPath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Face photo file not found on storage disk.");
        }

        return new FileSystemResource(path);
    }

    public StudentProfile findStudent(Long studentId) {
        return studentProfileRepository.findById(studentId)
                .or(() -> studentProfileRepository.findByUserId(studentId))
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found for ID: " + studentId));
    }

    private void validatePhotoFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            // Also inspect filename extension if content-type header is generic application/octet-stream
            String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")) {
                throw new IllegalArgumentException("Invalid file type (" + contentType + "). Only JPEG and PNG images are supported.");
            }
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Photo file size exceeds maximum allowable limit of 15 MB.");
        }
    }

    private String savePhotoToDisk(Long studentId, MultipartFile file) {
        try {
            Path storagePath = Paths.get(faceStorageDir);
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath);
            }

            String ext = "jpg";
            String original = file.getOriginalFilename();
            if (original != null && original.toLowerCase().endsWith(".png")) {
                ext = "png";
            }

            String filename = "student_" + studentId + "." + ext;
            Path destination = storagePath.resolve(filename);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist student face photo to storage disk: " + e.getMessage(), e);
        }
    }

    private String callAiServiceEnroll(MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo.jpg";
                }
            };
            body.add("image", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            String url = aiServiceUrl + "/ai/enroll";

            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("AI service failed to process face photo.");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error") && !root.get("error").isNull() && !root.get("error").asText().isBlank()) {
                throw new IllegalArgumentException(root.get("error").asText());
            }
            if (root.has("faceCount") && root.get("faceCount").asInt() != 1) {
                throw new IllegalArgumentException("Expected exactly 1 face, but detected " + root.get("faceCount").asInt());
            }
            if (root.has("faceDetected") && !root.get("faceDetected").asBoolean()) {
                throw new IllegalArgumentException("Expected exactly 1 face, but detected 0");
            }

            JsonNode embeddingNode = root.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray() || embeddingNode.size() != 512) {
                throw new IllegalStateException("AI service returned invalid embedding format (expected 512-d array).");
            }

            return objectMapper.writeValueAsString(embeddingNode);
        } catch (HttpStatusCodeException ex) {
            String errorDetail = "AI service face enrollment error.";
            try {
                JsonNode errNode = objectMapper.readTree(ex.getResponseBodyAsString());
                if (errNode.has("detail")) {
                    errorDetail = errNode.get("detail").asText();
                }
            } catch (Exception ignored) {
                errorDetail = ex.getStatusText();
            }
            throw new IllegalArgumentException(errorDetail);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read face photo bytes: " + e.getMessage(), e);
        }
    }
}