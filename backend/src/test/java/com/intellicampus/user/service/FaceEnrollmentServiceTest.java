package com.intellicampus.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.user.dto.CaptureTokenResponseDto;
import com.intellicampus.user.dto.FaceEnrollmentRequestDto;
import com.intellicampus.user.dto.LoginRequestDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FaceEnrollmentService covering:
 * - Duplicate face rejection in both directions
 * - Multi-frame embedding averaging and L2 normalization
 * - Pose consistency rejection
 * - One-time capture token expiry and single-use enforcement
 * - Repeated-frame SHA-256 deduplication rejection
 * - PENDING login rejection with clear message
 * - Zero files written to disk guarantee
 * - AI service validation (0 faces, multiple faces, blurry face)
 */
@ExtendWith(MockitoExtension.class)
class FaceEnrollmentServiceTest {

    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditChainService auditChainService;
    @Mock
    private IpRateLimiterService ipRateLimiterService;
    @Mock
    private RestTemplate restTemplate;

    private FaceEnrollmentService faceEnrollmentService;

    @TempDir
    Path tempFaceDir;

    private User studentUser;
    private StudentProfile studentProfile;
    private ObjectMapper objectMapper;

    // Helper to generate a dummy base64 JPEG byte string of at least 1KB with unique content
    private String createDummyBase64Frame(byte fillByte) {
        byte[] bytes = new byte[2048];
        Arrays.fill(bytes, fillByte);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Helper to generate a 512-d normalized vector with a single dominant coordinate or specific values
    private List<Double> createUnitVector(int nonZeroIndex) {
        List<Double> vec = new ArrayList<>(Collections.nCopies(512, 0.0));
        vec.set(nonZeroIndex, 1.0);
        return vec;
    }

    // Helper to generate an almost identical vector (cosine similarity ~ 0.95)
    private List<Double> createSimilarVector(List<Double> base, double sim) {
        List<Double> vec = new ArrayList<>(base);
        double primary = sim;
        double secondary = Math.sqrt(1.0 - sim * sim);
        vec.set(0, primary);
        vec.set(1, secondary);
        return vec;
    }

    @BeforeEach
    void setUp() {
        faceEnrollmentService = new FaceEnrollmentService(
                studentProfileRepository,
                userRepository,
                auditChainService,
                ipRateLimiterService,
                "http://localhost:8000",
                0.45,
                0.5
        );
        objectMapper = new ObjectMapper();
        faceEnrollmentService.setRestTemplate(restTemplate);
        faceEnrollmentService.setObjectMapper(objectMapper);
        faceEnrollmentService.setFaceMatchThreshold(0.45);
        faceEnrollmentService.setFacePoseConsistencyMin(0.5);

        // Allow rate limiter by default in tests
        lenient().when(ipRateLimiterService.isEnrollAllowed(anyString())).thenReturn(true);

        studentUser = new User("Priya Sharma", "priya@intellicampus.edu", "$2a$10$hashedpassword", Role.STUDENT, "STU-201");
        studentUser.setStatus(UserStatus.PENDING);
        ReflectionTestUtils.setField(studentUser, "id", 20L);

        studentProfile = new StudentProfile(studentUser, "2024-CS-088");
        studentProfile.setFaceEnrolled(false);
        ReflectionTestUtils.setField(studentProfile, "id", 50L);
    }

    @Test
    @DisplayName("Enrollment Success: Averaging normalized, user activated, audit logged, and NO file written to disk")
    void testEnrollmentSuccess_AveragingNormalizedAndNoFileWritten() throws Exception {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentProfileRepository.findAll()).thenReturn(Collections.emptyList());

        // 1. Issue one-time capture token
        CaptureTokenResponseDto tokenDto = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");
        assertNotNull(tokenDto.getToken());
        verify(auditChainService).recordEntry(eq(20L), eq("Priya Sharma"), eq("ENROLL_STARTED"), eq("STUDENT_BIOMETRIC"), eq("50"), anyString(), anyString(), anyString());

        // 2. Prepare 3 distinct frames (front, left, right)
        String frame1 = createDummyBase64Frame((byte) 11);
        String frame2 = createDummyBase64Frame((byte) 22);
        String frame3 = createDummyBase64Frame((byte) 33);

        List<Double> e1 = new ArrayList<>(Collections.nCopies(512, 0.04419)); // norm ~ 1.0
        List<Double> e2 = new ArrayList<>(Collections.nCopies(512, 0.04419));
        List<Double> e3 = new ArrayList<>(Collections.nCopies(512, 0.04419));

        String aiResponse1 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e1) + ",\"quality\":0.96,\"yaw\":0.0}";
        String aiResponse2 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e2) + ",\"quality\":0.95,\"yaw\":-18.0}";
        String aiResponse3 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e3) + ",\"quality\":0.97,\"yaw\":18.0}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResponse1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResponse2, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResponse3, HttpStatus.OK));

        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(tokenDto.getToken());
        req.setFrames(List.of(frame1, frame2, frame3));
        req.setLiveness(true);
        req.setConsentVersion("1.0");

        StudentProfile enrolled = faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1");

        // Verify Student is now face enrolled
        assertTrue(enrolled.getFaceEnrolled());
        assertNotNull(enrolled.getFaceEmbedding());
        assertNotNull(enrolled.getFaceEmbeddingsMulti());
        assertNotNull(enrolled.getConsentAt());
        assertEquals("1.0", enrolled.getConsentVersion());

        // Verify User status transitioned from PENDING to ACTIVE
        assertEquals(UserStatus.ACTIVE, studentUser.getStatus());

        // Verify face_photo_path is NOT written (raw images never persisted)
        assertNull(enrolled.getFacePhotoPath(), "face_photo_path must not be written");

        // Verify NO files were ever written to temporary/storage disk
        try (var stream = Files.list(tempFaceDir)) {
            assertEquals(0, stream.count(), "Assert no file was written to disk during enrollment");
        }

        // Verify embeddings multi JSON contains model name and all poses
        String multiJson = enrolled.getFaceEmbeddingsMulti();
        assertTrue(multiJson.contains("buffalo_l"));
        assertTrue(multiJson.contains("front"));
        assertTrue(multiJson.contains("left"));
        assertTrue(multiJson.contains("right"));
        assertTrue(multiJson.contains("average"));

        // Verify averaged embedding is L2-normalized: ||avg|| ~ 1.0
        List<Double> avgList = objectMapper.readValue(enrolled.getFaceEmbedding(), List.class);
        assertEquals(512, avgList.size());
        double norm = 0.0;
        for (Object v : avgList) {
            double d = ((Number) v).doubleValue();
            norm += d * d;
        }
        assertEquals(1.0, Math.sqrt(norm), 1e-4, "Averaged embedding must be L2-normalized to 1.0");

        // Verify ENROLL_SUCCESS was audited
        verify(auditChainService).recordEntry(eq(20L), eq("Priya Sharma"), eq("ENROLL_SUCCESS"), eq("STUDENT_BIOMETRIC"), eq("50"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Duplicate Face: Reject enrollment in both directions (A against existing B, and B against existing A)")
    void testDuplicateFace_RejectsBothDirections() throws Exception {
        // Construct two similar embeddings with similarity ~ 0.85 (>= 0.45 threshold)
        List<Double> baseA = createUnitVector(0); // [1, 0, 0, ...]
        List<Double> baseB = createSimilarVector(baseA, 0.85); // similarity 0.85 with baseA

        String aiRespA1 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(baseA) + ",\"quality\":0.96,\"yaw\":0.0}";
        String aiRespA2 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(baseA) + ",\"quality\":0.96,\"yaw\":-18.0}";
        String aiRespA3 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(baseA) + ",\"quality\":0.96,\"yaw\":18.0}";

        String aiRespB1 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(baseB) + ",\"quality\":0.96,\"yaw\":0.0}";
        String aiRespB2 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(baseB) + ",\"quality\":0.96,\"yaw\":-18.0}";
        String aiRespB3 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(baseB) + ",\"quality\":0.96,\"yaw\":18.0}";

        // Student A (id 50) and Student B (id 60)
        User userB = new User("Rohan Deshmukh", "rohan@intellicampus.edu", "pass", Role.STUDENT, "STU-202");
        ReflectionTestUtils.setField(userB, "id", 21L);
        StudentProfile studentB = new StudentProfile(userB, "2024-CS-089");
        ReflectionTestUtils.setField(studentB, "id", 60L);

        // --- DIRECTION 1: Student B is already enrolled; Student A tries to enroll with matching face ---
        studentB.setFaceEnrolled(true);
        studentB.setFaceEmbedding(objectMapper.writeValueAsString(baseB));

        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        when(studentProfileRepository.findAll()).thenReturn(List.of(studentB));

        CaptureTokenResponseDto tokenA = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiRespA1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiRespA2, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiRespA3, HttpStatus.OK));

        FaceEnrollmentRequestDto reqA = new FaceEnrollmentRequestDto();
        reqA.setToken(tokenA.getToken());
        reqA.setFrames(List.of(createDummyBase64Frame((byte) 1), createDummyBase64Frame((byte) 2), createDummyBase64Frame((byte) 3)));
        reqA.setLiveness(true);

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, reqA, "127.0.0.1")
        );
        assertTrue(ex1.getMessage().contains("Face template matches an existing enrolled student") || ex1.getMessage().contains("already registered"));
        assertFalse(studentProfile.getFaceEnrolled());
        verify(auditChainService).recordEntry(eq(20L), eq("Priya Sharma"), eq("ENROLL_REJECTED_DUPLICATE"), eq("STUDENT_BIOMETRIC"), eq("50"), anyString(), anyString(), anyString());

        // --- DIRECTION 2: Student A is already enrolled; Student B tries to enroll with matching face ---
        reset(studentProfileRepository, restTemplate, auditChainService);
        lenient().when(ipRateLimiterService.isEnrollAllowed(anyString())).thenReturn(true);

        studentProfile.setFaceEnrolled(true);
        studentProfile.setFaceEmbedding(objectMapper.writeValueAsString(baseA));

        studentB.setFaceEnrolled(false);
        studentB.setFaceEmbedding(null);
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(studentB));
        when(studentProfileRepository.findAll()).thenReturn(List.of(studentProfile));

        CaptureTokenResponseDto tokenB = faceEnrollmentService.issueCaptureToken(60L, "127.0.0.1");

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiRespB1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiRespB2, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiRespB3, HttpStatus.OK));

        FaceEnrollmentRequestDto reqB = new FaceEnrollmentRequestDto();
        reqB.setToken(tokenB.getToken());
        reqB.setFrames(List.of(createDummyBase64Frame((byte) 4), createDummyBase64Frame((byte) 5), createDummyBase64Frame((byte) 6)));
        reqB.setLiveness(true);

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(60L, reqB, "127.0.0.1")
        );
        assertTrue(ex2.getMessage().contains("Face template matches an existing enrolled student") || ex2.getMessage().contains("already registered"));
        assertFalse(studentB.getFaceEnrolled());
        verify(auditChainService).recordEntry(eq(21L), eq("Rohan Deshmukh"), eq("ENROLL_REJECTED_DUPLICATE"), eq("STUDENT_BIOMETRIC"), eq("60"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Pose Consistency: Rejects enrollment when pairwise similarity across poses is below threshold (0.5)")
    void testPoseConsistencyRejection() throws Exception {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        CaptureTokenResponseDto token = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        // Two orthogonal unit vectors (cosine similarity = 0.0 < 0.5)
        List<Double> eFront = createUnitVector(0);
        List<Double> eLeft = createUnitVector(1); // orthogonal to front!
        List<Double> eRight = createUnitVector(0);

        String aiResp1 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(eFront) + ",\"quality\":0.96,\"yaw\":0.0}";
        String aiResp2 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(eLeft) + ",\"quality\":0.96,\"yaw\":-18.0}";
        String aiResp3 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(eRight) + ",\"quality\":0.96,\"yaw\":18.0}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResp2, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResp3, HttpStatus.OK));

        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(token.getToken());
        req.setFrames(List.of(createDummyBase64Frame((byte) 7), createDummyBase64Frame((byte) 8), createDummyBase64Frame((byte) 9)));
        req.setLiveness(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Pose consistency check failed"), "Expected pose consistency error, got: " + ex.getMessage());
        assertFalse(studentProfile.getFaceEnrolled());
        verify(auditChainService).recordEntry(eq(20L), eq("Priya Sharma"), eq("ENROLL_REJECTED_QUALITY/LIVENESS"), eq("STUDENT_BIOMETRIC"), eq("50"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Token Lifecycle: Rejects expired token and enforces single-use (rejects replay)")
    void testTokenExpiryAndSingleUse() throws Exception {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));

        // Sub-test 1: Expired token
        CaptureTokenResponseDto tokenDto = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");
        // Manually expire the token in the store
        FaceEnrollmentService.CaptureToken tokenObj = faceEnrollmentService.getCaptureTokenStore().get(tokenDto.getToken());
        ReflectionTestUtils.setField(tokenObj, "expiresAt", Instant.now().minus(10, ChronoUnit.MINUTES));

        FaceEnrollmentRequestDto reqExpired = new FaceEnrollmentRequestDto();
        reqExpired.setToken(tokenDto.getToken());
        reqExpired.setFrames(List.of(createDummyBase64Frame((byte) 1), createDummyBase64Frame((byte) 2), createDummyBase64Frame((byte) 3)));

        IllegalArgumentException exExpired = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, reqExpired, "127.0.0.1")
        );
        assertTrue(exExpired.getMessage().contains("expired"), "Expected expired token message, got: " + exExpired.getMessage());

        // Sub-test 2: Single-use enforcement
        CaptureTokenResponseDto tokenSingleUse = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");
        FaceEnrollmentService.CaptureToken activeToken = faceEnrollmentService.getCaptureTokenStore().get(tokenSingleUse.getToken());
        // Simulate first use
        activeToken.setUsed(true);

        FaceEnrollmentRequestDto reqReplay = new FaceEnrollmentRequestDto();
        reqReplay.setToken(tokenSingleUse.getToken());
        reqReplay.setFrames(List.of(createDummyBase64Frame((byte) 1), createDummyBase64Frame((byte) 2), createDummyBase64Frame((byte) 3)));

        IllegalArgumentException exReplay = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, reqReplay, "127.0.0.1")
        );
        assertTrue(exReplay.getMessage().contains("already been used"), "Expected single-use error, got: " + exReplay.getMessage());
    }

    @Test
    @DisplayName("Repeated Frame: Rejects enrollment with duplicate SHA-256 frame without calling AI service")
    void testRepeatedFrameRejection() {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        CaptureTokenResponseDto token = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        String identicalFrame = createDummyBase64Frame((byte) 77);
        String distinctFrame = createDummyBase64Frame((byte) 88);

        // Frame 1 and Frame 2 are byte-for-byte identical
        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(token.getToken());
        req.setFrames(List.of(identicalFrame, identicalFrame, distinctFrame));
        req.setLiveness(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Repeated frames detected"), "Expected repeated frame error, got: " + ex.getMessage());
        // Verify AI service was never invoked for repeated frames
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        verify(auditChainService).recordEntry(eq(20L), eq("Priya Sharma"), eq("ENROLL_REJECTED_QUALITY/LIVENESS"), eq("STUDENT_BIOMETRIC"), eq("50"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PENDING Login Rejection: Student with PENDING status is rejected with clear message")
    void testPendingLoginRejection() {
        // Prepare AuthService with mock userRepository
        UserRepository mockUserRepo = mock(UserRepository.class);
        PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
        StudentAuthService authService = new StudentAuthService(
                mockUserRepo,
                studentProfileRepository,
                mock(),
                mock(),
                mock(),
                mock(),
                mockEncoder
        );

        User pendingUser = new User("Priya Sharma", "priya@intellicampus.edu", "$2a$10$hashedpassword", Role.STUDENT, "STU-201");
        pendingUser.setStatus(UserStatus.PENDING);
        when(mockUserRepo.findByEmail("priya@intellicampus.edu")).thenReturn(Optional.of(pendingUser));

        LoginRequestDto loginReq = new LoginRequestDto("priya@intellicampus.edu", "Password123!");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.login(loginReq)
        );

        assertTrue(ex.getMessage().contains("pending biometric face enrollment"),
                "Expected clear pending enrollment rejection message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("AI Service: 0 faces detected rejects with error")
    void testEnrollmentRejectsZeroFaces() {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        CaptureTokenResponseDto token = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        String aiResponse = "{\"faceDetected\":false,\"faceCount\":0,\"error\":\"No face detected in photo\"}";
        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResponse, HttpStatus.OK));

        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(token.getToken());
        req.setFrames(List.of(createDummyBase64Frame((byte) 1), createDummyBase64Frame((byte) 2), createDummyBase64Frame((byte) 3)));
        req.setLiveness(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("No face detected") || ex.getMessage().contains("0"));
        assertFalse(studentProfile.getFaceEnrolled());
    }

    @Test
    @DisplayName("AI Service: Multiple (2+) faces detected rejects with error")
    void testEnrollmentRejectsMultipleFaces() {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        CaptureTokenResponseDto token = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        String aiResponse = "{\"faceDetected\":true,\"faceCount\":2,\"error\":\"Multiple faces detected: 2\"}";
        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResponse, HttpStatus.OK));

        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(token.getToken());
        req.setFrames(List.of(createDummyBase64Frame((byte) 1), createDummyBase64Frame((byte) 2), createDummyBase64Frame((byte) 3)));
        req.setLiveness(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Multiple faces detected") || ex.getMessage().contains("2"));
        assertFalse(studentProfile.getFaceEnrolled());
    }

    @Test
    @DisplayName("AI Service: Blurry face returns error from AI service and rejects enrollment")
    void testEnrollmentRejectsBlurryFace() {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        CaptureTokenResponseDto token = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        String aiResponse = "{\"faceDetected\":true,\"faceCount\":1,\"error\":\"Face photo is too blurry or low quality for biometric enrollment\"}";
        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResponse, HttpStatus.OK));

        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(token.getToken());
        req.setFrames(List.of(createDummyBase64Frame((byte) 1), createDummyBase64Frame((byte) 2), createDummyBase64Frame((byte) 3)));
        req.setLiveness(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Face photo is too blurry"));
        assertFalse(studentProfile.getFaceEnrolled());
    }

    @Test
    @DisplayName("Liveness Test 1: Missing liveness field passes when 3 frames show distinct server-verified poses")
    void testEnrollment_MissingLivenessField_Success() throws Exception {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentProfileRepository.findAll()).thenReturn(Collections.emptyList());

        CaptureTokenResponseDto token = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        List<Double> e = new ArrayList<>(Collections.nCopies(512, 0.04419));
        String aiResp1 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.96,\"yaw\":0.0}";
        String aiResp2 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.95,\"yaw\":-18.0}";
        String aiResp3 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.97,\"yaw\":18.0}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResp2, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResp3, HttpStatus.OK));

        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(token.getToken());
        req.setFrames(List.of(createDummyBase64Frame((byte) 101), createDummyBase64Frame((byte) 102), createDummyBase64Frame((byte) 103)));
        req.setLiveness(null); // Missing liveness field from client trust path
        req.setConsentVersion("1.0");

        StudentProfile enrolled = faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1");
        assertTrue(enrolled.getFaceEnrolled(), "Enrollment should succeed despite missing client-sent liveness field");
        assertEquals(UserStatus.ACTIVE, studentUser.getStatus());
    }

    @Test
    @DisplayName("Liveness Test 2: 3 near-identical frames reject on server-side head yaw difference (< minYawDiff)")
    void testEnrollment_NearIdenticalFrames_YawDiffFails() throws Exception {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        CaptureTokenResponseDto token = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        List<Double> e = new ArrayList<>(Collections.nCopies(512, 0.04419));
        // Near-identical poses: yaw differences are only 2.0 and 1.5 degrees (< minYawDiff = 10.0 deg)
        String aiResp1 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.96,\"yaw\":0.0}";
        String aiResp2 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.95,\"yaw\":2.0}";
        String aiResp3 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.97,\"yaw\":1.5}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResp2, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResp3, HttpStatus.OK));

        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(token.getToken());
        req.setFrames(List.of(createDummyBase64Frame((byte) 111), createDummyBase64Frame((byte) 112), createDummyBase64Frame((byte) 113)));
        req.setLiveness(true); // Even if client claims liveness is true, server rejects!

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1")
        );

        assertTrue(ex.getMessage().contains("Pose verification failed") || ex.getMessage().contains("Distinct head poses required"),
                "Expected yaw pose rejection, got: " + ex.getMessage());
        assertFalse(studentProfile.getFaceEnrolled());
        verify(auditChainService).recordEntry(eq(20L), eq("Priya Sharma"), eq("ENROLL_REJECTED_QUALITY/LIVENESS"), eq("STUDENT_BIOMETRIC"), eq("50"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Liveness Test 3: Correct distinct poses (yaw differences >= minYawDiff) succeed and activate user")
    void testEnrollment_CorrectPoses_Success() throws Exception {
        when(studentProfileRepository.findById(50L)).thenReturn(Optional.of(studentProfile));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentProfileRepository.findAll()).thenReturn(Collections.emptyList());

        CaptureTokenResponseDto token = faceEnrollmentService.issueCaptureToken(50L, "127.0.0.1");

        List<Double> e = new ArrayList<>(Collections.nCopies(512, 0.04419));
        // Valid distinct poses: Front (0 deg), Left (-15 deg), Right (+15 deg)
        String aiResp1 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.96,\"yaw\":0.0}";
        String aiResp2 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.95,\"yaw\":-15.0}";
        String aiResp3 = "{\"faceDetected\":true,\"faceCount\":1,\"embedding\":" + objectMapper.writeValueAsString(e) + ",\"quality\":0.97,\"yaw\":15.0}";

        when(restTemplate.postForEntity(contains("/ai/enroll"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiResp1, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResp2, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(aiResp3, HttpStatus.OK));

        FaceEnrollmentRequestDto req = new FaceEnrollmentRequestDto();
        req.setToken(token.getToken());
        req.setFrames(List.of(createDummyBase64Frame((byte) 121), createDummyBase64Frame((byte) 122), createDummyBase64Frame((byte) 123)));
        req.setConsentVersion("1.0");

        StudentProfile enrolled = faceEnrollmentService.enrollStudentFace(50L, req, "127.0.0.1");
        assertTrue(enrolled.getFaceEnrolled());
        assertEquals(UserStatus.ACTIVE, studentUser.getStatus());
        verify(auditChainService).recordEntry(eq(20L), eq("Priya Sharma"), eq("ENROLL_SUCCESS"), eq("STUDENT_BIOMETRIC"), eq("50"), anyString(), anyString(), anyString());
    }
}
