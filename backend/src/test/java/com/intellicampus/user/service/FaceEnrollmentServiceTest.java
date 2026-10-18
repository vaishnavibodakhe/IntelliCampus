package com.intellicampus.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FaceEnrollmentService covering face enrollment with 0 faces,
 * 2 faces, 1 face (success), blurry face error, and photo download authorization.
 */
@ExtendWith(MockitoExtension.class)
class FaceEnrollmentServiceTest {

    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private FaceEnrollmentService faceEnrollmentService;

    @TempDir
    Path tempFaceDir;

    private User studentUser;
    private StudentProfile studentProfile;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        faceEnrollmentService.setRestTemplate(restTemplate);
        faceEnrollmentService.setObjectMapper(objectMapper);
        ReflectionTestUtils.setField(faceEnrollmentService, "faceStorageDir", tempFaceDir.toString());

        studentUser = new User("Priya Sharma", "priya@intellicampus.edu", "password", Role.STUDENT, "STU-201");
        ReflectionTestUtils.setField(studentUser, "id", 20L);

        studentProfile = new StudentProfile(studentUser, "2024-CS-088");
        ReflectionTestUtils.setField(studentProfile, "id", 50L);
    }

    @Test
    @DisplayName("Enrollment Success: Exactly 1 face detected extracts 512-d embedding and marks face_enrolled")
    void testEnrollmentSuccess() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "face.jpg", "image/jpeg", new byte[]{10, 20, 30, 40}
        );

        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Double> embedding = new ArrayList<>();
        for (int i = 0; i < 512; i++) embedding.add(0.044);

        String aiResponse = "{" +
                "\"faceDetected\": true," +
                "\"faceCount\": 1," +
                "\"embedding\": " + objectMapper.writeValueAsString(embedding) + "," +
                "\"quality\": 0.96" +
                "}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResponse, HttpStatus.OK));

        faceEnrollmentService.enrollStudentFace(50L, photo, "1.0");

        assertTrue(studentProfile.getFaceEnrolled());
        assertNotNull(studentProfile.getFaceEmbedding());
        assertNotNull(studentProfile.getFacePhotoPath());
        assertNotNull(studentProfile.getConsentAt());
        assertEquals("1.0", studentProfile.getConsentVersion());
        verify(studentProfileRepository).save(studentProfile);
    }

    @Test
    @DisplayName("Enrollment Rejection: 0 faces detected throws IllegalArgumentException")
    void testEnrollmentRejectsZeroFaces() {
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "blank.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));

        String aiResponse = "{" +
                "\"faceDetected\": false," +
                "\"faceCount\": 0," +
                "\"error\": \"No face detected in photo\"" +
                "}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResponse, HttpStatus.OK));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, photo, "1.0")
        );

        assertTrue(ex.getMessage().contains("No face detected") || ex.getMessage().contains("0"));
        assertFalse(studentProfile.getFaceEnrolled());
    }

    @Test
    @DisplayName("Enrollment Rejection: Multiple (2+) faces detected throws IllegalArgumentException")
    void testEnrollmentRejectsMultipleFaces() {
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "group.jpg", "image/jpeg", new byte[]{1, 2, 3, 4, 5}
        );

        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));

        String aiResponse = "{" +
                "\"faceDetected\": true," +
                "\"faceCount\": 2," +
                "\"error\": \"Multiple faces detected: 2\"" +
                "}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResponse, HttpStatus.OK));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, photo, "1.0")
        );

        assertTrue(ex.getMessage().contains("Multiple faces detected") || ex.getMessage().contains("2"));
        assertFalse(studentProfile.getFaceEnrolled());
    }

    @Test
    @DisplayName("Enrollment Rejection: Blurry or small face returns error from AI service")
    void testEnrollmentRejectsBlurryFace() {
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "blurry.jpg", "image/jpeg", new byte[]{9, 8, 7}
        );

        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));

        String aiResponse = "{" +
                "\"faceDetected\": true," +
                "\"faceCount\": 1," +
                "\"error\": \"Face photo is too blurry or low quality for biometric enrollment\"" +
                "}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResponse, HttpStatus.OK));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, photo, "1.0")
        );

        assertTrue(ex.getMessage().contains("Face photo is too blurry"));
        assertFalse(studentProfile.getFaceEnrolled());
    }

    @Test
    @DisplayName("Photo Download Access Control: ADMIN and HOD allowed, others rejected")
    void testPhotoDownloadAccessControl() throws Exception {
        // Prepare enrolled student
        Path photoFile = tempFaceDir.resolve("student_50.jpg");
        studentProfile.setFacePhotoPath(photoFile.toString());
        studentProfile.setFaceEnrolled(true);
        java.nio.file.Files.write(photoFile, new byte[]{1, 2, 3});

        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));

        User adminUser = new User("Admin", "admin@ic.edu", "pass", Role.ADMIN, "ADM-01");
        ReflectionTestUtils.setField(adminUser, "id", 1L);

        User studentOther = new User("Student", "other@ic.edu", "pass", Role.STUDENT, "STU-99");
        ReflectionTestUtils.setField(studentOther, "id", 99L);

        // Admin download succeeds
        Resource resource = faceEnrollmentService.loadStudentFacePhoto(50L, adminUser);
        assertNotNull(resource);
        assertTrue(resource.exists());

        // Student download fails with SecurityException
        assertThrows(SecurityException.class, () ->
                faceEnrollmentService.loadStudentFacePhoto(50L, studentOther)
        );
    }
}
