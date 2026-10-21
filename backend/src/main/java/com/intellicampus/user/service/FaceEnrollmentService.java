package com.intellicampus.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.user.dto.CaptureTokenResponseDto;
import com.intellicampus.user.dto.FaceEnrollmentRequestDto;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Biometric Face Enrollment Service.
 *
 * Team requirement: face images must come ONLY from the live camera
 * (no file upload, no gallery, no raw image storage).
 *
 * NOTE ON LIVENESS & SPOOF DETECTION:
 * Client-side liveness checks (e.g., blink detection, head turns, micro-motion)
 * are best-effort heuristics. A remote server cannot cryptographically prove that an image
 * originated directly from a physical camera sensor rather than a virtual camera or loopback.
 * Therefore, client-sent liveness booleans are excluded from the trust path.
 * The system enforces server-side defense in depth:
 * 1. Single-use token & nonce bound to pending account and IP.
 * 2. Per-frame SHA-256 deduplication (rejecting identical frames).
 * 3. AI single-face detection on each frame.
 * 4. Head yaw angle divergence from AI service (/ai/enroll) requiring left and right
 *    frames to differ from front pose by at least FACE_MIN_YAW_DIFF (default 10 deg).
 * 5. Pairwise embedding cosine consistency (>= FACE_POSE_CONSISTENCY_MIN, default 0.50).
 * 6. Duplicate face detection against existing enrolled students (>= FACE_MATCH_THRESHOLD, default 0.45).
 */
@Service
@Transactional
public class FaceEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(FaceEnrollmentService.class);

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final AuditChainService auditChainService;
    private final IpRateLimiterService ipRateLimiterService;

    private final String aiServiceUrl;
    private double faceMatchThreshold;
    private double facePoseConsistencyMin;
    private double faceMinYawDiff;

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    // In-memory token store for single-use capture tokens (5 min TTL)
    private final Map<String, CaptureToken> captureTokenStore = new ConcurrentHashMap<>();

    public static class FaceFrameData {
        private final double[] embedding;
        private final double yaw;

        public FaceFrameData(double[] embedding, double yaw) {
            this.embedding = embedding;
            this.yaw = yaw;
        }

        public double[] getEmbedding() { return embedding; }
        public double getYaw() { return yaw; }
    }

    public static class CaptureToken {
        private final String token;
        private final Long studentId;
        private final String clientIp;
        private final Instant expiresAt;
        private boolean used;

        public CaptureToken(String token, Long studentId, String clientIp, Instant expiresAt) {
            this.token = token;
            this.studentId = studentId;
            this.clientIp = clientIp;
            this.expiresAt = expiresAt;
            this.used = false;
        }

        public String getToken() { return token; }
        public Long getStudentId() { return studentId; }
        public String getClientIp() { return clientIp; }
        public Instant getExpiresAt() { return expiresAt; }
        public boolean isUsed() { return used; }
        public void setUsed(boolean used) { this.used = used; }
    }

    public FaceEnrollmentService() {
        this.studentProfileRepository = null;
        this.userRepository = null;
        this.auditChainService = null;
        this.ipRateLimiterService = null;
        this.aiServiceUrl = "http://localhost:8000";
        this.faceMatchThreshold = 0.45;
        this.facePoseConsistencyMin = 0.5;
        this.faceMinYawDiff = 10.0;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public FaceEnrollmentService(
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            AuditChainService auditChainService,
            IpRateLimiterService ipRateLimiterService,
            String aiServiceUrl,
            double faceMatchThreshold,
            double facePoseConsistencyMin) {
        this(studentProfileRepository, userRepository, auditChainService, ipRateLimiterService,
                aiServiceUrl, faceMatchThreshold, facePoseConsistencyMin, 10.0);
    }

    @Autowired
    public FaceEnrollmentService(
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            AuditChainService auditChainService,
            IpRateLimiterService ipRateLimiterService,
            @Value("${app.ai.service-url:http://localhost:8000}") String aiServiceUrl,
            @Value("${FACE_MATCH_THRESHOLD:${app.attendance.face-match-threshold:0.45}}") double faceMatchThreshold,
            @Value("${FACE_POSE_CONSISTENCY_MIN:0.5}") double facePoseConsistencyMin,
            @Value("${FACE_MIN_YAW_DIFF:10.0}") double faceMinYawDiff) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.auditChainService = auditChainService;
        this.ipRateLimiterService = ipRateLimiterService;
        this.aiServiceUrl = aiServiceUrl;
        this.faceMatchThreshold = faceMatchThreshold;
        this.facePoseConsistencyMin = facePoseConsistencyMin;
        this.faceMinYawDiff = faceMinYawDiff;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setFaceMatchThreshold(double faceMatchThreshold) {
        this.faceMatchThreshold = faceMatchThreshold;
    }

    public void setFacePoseConsistencyMin(double facePoseConsistencyMin) {
        this.facePoseConsistencyMin = facePoseConsistencyMin;
    }

    public void setFaceMinYawDiff(double faceMinYawDiff) {
        this.faceMinYawDiff = faceMinYawDiff;
    }

    public double getFaceMinYawDiff() {
        return faceMinYawDiff;
    }

    public StudentProfileRepository getStudentProfileRepository() {
        return studentProfileRepository;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public AuditChainService getAuditChainService() {
        return auditChainService;
    }

    public IpRateLimiterService getIpRateLimiterService() {
        return ipRateLimiterService;
    }

    public Map<String, CaptureToken> getCaptureTokenStore() {
        return captureTokenStore;
    }

    /**
     * Issues a one-time capture token (nonce, 5 min expiry, bound to pending account and IP).
     */
    public CaptureTokenResponseDto issueCaptureToken(Long studentId, String clientIp) {
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID is required to issue capture token.");
        }

        // Rate limit enroll requests per IP and per pending account
        checkRateLimits(clientIp, studentId);

        StudentProfile student = findStudent(studentId);

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        CaptureToken captureToken = new CaptureToken(token, student.getId(), clientIp, expiresAt);
        captureTokenStore.put(token, captureToken);

        // Audit entry: ENROLL_STARTED
        Long actorId = student.getUser() != null ? student.getUser().getId() : student.getId();
        String actorName = student.getUser() != null ? student.getUser().getName() : "Student";
        auditChainService.recordEntry(
                actorId,
                actorName,
                "ENROLL_STARTED",
                "STUDENT_BIOMETRIC",
                String.valueOf(student.getId()),
                "{}",
                "{\"status\":\"TOKEN_ISSUED\"}",
                "Biometric capture session initialized with one-time token"
        );

        // Log without sensitive student ID or IP
        log.info("Issued face capture token.");
        return new CaptureTokenResponseDto(token, student.getId(), 300, expiresAt);
    }

    /**
     * Processes live camera face enrollment with exactly 3 poses (front, left, right).
     * Validates capture token, image deduplication, AI 1-face check, head yaw divergence,
     * pose consistency, duplicate face check, and activates student upon success.
     * Note: No file is ever saved to disk; raw face frames are strictly discarded after embedding extraction.
     */
    public StudentProfile enrollStudentFace(Long studentId, FaceEnrollmentRequestDto req, String clientIp) {
        if (req == null) {
            throw new IllegalArgumentException("Face enrollment payload is mandatory.");
        }

        checkRateLimits(clientIp, studentId);

        StudentProfile student = findStudent(studentId);
        Long actorId = student.getUser() != null ? student.getUser().getId() : student.getId();
        String actorName = student.getUser() != null ? student.getUser().getName() : "Student";

        try {
            // 1. Server-side check: Valid unexpired unused token (single use)
            validateCaptureToken(req.getToken(), student.getId(), clientIp);

            // 2. Client-sent liveness boolean is excluded from trust path.
            // Server enforces true server-side liveness verification via head pose (yaw divergence) and anti-spoof checks.

            // 3. Server-side check: Frame count must be exactly 3
            List<String> frames = req.getFrames();
            if (frames == null || frames.size() != 3) {
                throw new QualityOrLivenessException("Biometric enrollment requires exactly 3 frames (front, left, right). Received: " + (frames != null ? frames.size() : 0));
            }

            // 4. Decode frames and enforce size limits (1KB - 5MB)
            List<byte[]> frameBytes = new ArrayList<>();
            List<String> frameHashes = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                byte[] decoded = decodeBase64Image(frames.get(i));
                if (decoded.length < 1024 || decoded.length > 5 * 1024 * 1024) {
                    throw new QualityOrLivenessException(String.format("Frame %d size (%d bytes) is outside allowable limits (1 KB to 5 MB).", i + 1, decoded.length));
                }
                frameBytes.add(decoded);
                frameHashes.add(sha256Hex(decoded));
            }

            // 5. Server-side check: Per-frame SHA-256 hash dedupe (reject repeated images)
            if (frameHashes.get(0).equals(frameHashes.get(1)) ||
                frameHashes.get(0).equals(frameHashes.get(2)) ||
                frameHashes.get(1).equals(frameHashes.get(2))) {
                throw new QualityOrLivenessException("Repeated frames detected. Each of the 3 poses (front, left, right) must be distinct live captures.");
            }

            // 6. AI service enrollment: Each frame must contain exactly 1 face and returns embedding + head yaw
            FaceFrameData[] frameData = new FaceFrameData[3];
            double[][] embeddings = new double[3][];
            for (int i = 0; i < 3; i++) {
                try {
                    frameData[i] = callAiServiceEnroll(frameBytes.get(i));
                    embeddings[i] = frameData[i].getEmbedding();
                } catch (Exception ex) {
                    throw new QualityOrLivenessException("AI face analysis failed on frame " + (i + 1) + ": " + ex.getMessage(), ex);
                }
            }

            // 7. Server-side check: Distinct head pose verification via head yaw from AI service
            // Frame 0 = front, Frame 1 = left, Frame 2 = right
            double yawFront = frameData[0].getYaw();
            double yawLeft = frameData[1].getYaw();
            double yawRight = frameData[2].getYaw();

            double diffLeftFront = Math.abs(yawLeft - yawFront);
            double diffRightFront = Math.abs(yawRight - yawFront);
            double diffLeftRight = Math.abs(yawLeft - yawRight);

            if (diffLeftFront < faceMinYawDiff || diffRightFront < faceMinYawDiff || diffLeftRight < faceMinYawDiff) {
                String detail = String.format("Pose verification failed: Distinct head poses required (front, left, right). Detected yaws: Front=%.1f deg, Left=%.1f deg, Right=%.1f deg. Minimum required yaw difference is %.1f deg.",
                        yawFront, yawLeft, yawRight, faceMinYawDiff);
                throw new QualityOrLivenessException(detail);
            }

            // 8. Server-side check: Pose consistency (pairwise cosine >= FACE_POSE_CONSISTENCY_MIN, default 0.5)
            double sim01 = cosineSimilarity(embeddings[0], embeddings[1]);
            double sim02 = cosineSimilarity(embeddings[0], embeddings[2]);
            double sim12 = cosineSimilarity(embeddings[1], embeddings[2]);
            if (sim01 < facePoseConsistencyMin || sim02 < facePoseConsistencyMin || sim12 < facePoseConsistencyMin) {
                String detail = String.format("Pose consistency check failed (pairwise cosine similarities: front-left=%.3f, front-right=%.3f, left-right=%.3f; required >= %.2f)",
                        sim01, sim02, sim12, facePoseConsistencyMin);
                throw new QualityOrLivenessException(detail);
            }

            // 9. Compute L2-normalized average embedding
            double[] averageNormalized = computeAverageNormalizedEmbedding(embeddings);

            // 10. Duplicate face check: compare average against all other enrolled students
            checkForDuplicateFace(student.getId(), averageNormalized);

            // 11. Store embeddings in DB:
            // Store each embedding plus L2-normalized average in students.face_embeddings_multi (JSON) with model name/version
            Map<String, Object> multiObj = new LinkedHashMap<>();
            multiObj.put("model", "buffalo_l");
            multiObj.put("version", "1.0");
            multiObj.put("dimension", 512);

            Map<String, Object> frontMap = new LinkedHashMap<>();
            frontMap.put("embedding", toDoubleList(embeddings[0]));
            frontMap.put("yaw", yawFront);

            Map<String, Object> leftMap = new LinkedHashMap<>();
            leftMap.put("embedding", toDoubleList(embeddings[1]));
            leftMap.put("yaw", yawLeft);

            Map<String, Object> rightMap = new LinkedHashMap<>();
            rightMap.put("embedding", toDoubleList(embeddings[2]));
            rightMap.put("yaw", yawRight);

            Map<String, Object> posesMap = new LinkedHashMap<>();
            posesMap.put("front", frontMap);
            posesMap.put("left", leftMap);
            posesMap.put("right", rightMap);
            multiObj.put("embeddings", posesMap);
            multiObj.put("average", toDoubleList(averageNormalized));

            String multiJson = objectMapper.writeValueAsString(multiObj);
            String avgJson = objectMapper.writeValueAsString(toDoubleList(averageNormalized));

            student.setFaceEmbeddingsMulti(multiJson);
            student.setFaceEmbedding(avgJson);
            student.setFaceEnrolled(true);
            student.setConsentAt(LocalDateTime.now());
            student.setConsentVersion(req.getConsentVersion() != null && !req.getConsentVersion().isBlank()
                    ? req.getConsentVersion()
                    : "1.0");
            // DO NOT write face_photo_path (leave null, no file on disk)
            studentProfileRepository.save(student);

            // Activate student User account
            User user = student.getUser();
            if (user != null) {
                user.setStatus(UserStatus.ACTIVE);
                userRepository.save(user);
            }

            // Audit entry: ENROLL_SUCCESS
            auditChainService.recordEntry(
                    actorId,
                    actorName,
                    "ENROLL_SUCCESS",
                    "STUDENT_BIOMETRIC",
                    String.valueOf(student.getId()),
                    "{\"status\":\"PENDING\"}",
                    "{\"status\":\"ACTIVE\",\"consentVersion\":\"" + student.getConsentVersion() + "\"}",
                    "Biometric face enrollment completed successfully across 3 frames without file storage"
            );

            log.info("Student face enrolled successfully and activated.");
            return student;

        } catch (DuplicateFaceException ex) {
            ipRateLimiterService.recordEnrollAttempt("ip:" + clientIp);
            ipRateLimiterService.recordEnrollAttempt("account:" + studentId);
            auditChainService.recordEntry(
                    actorId,
                    actorName,
                    "ENROLL_REJECTED_DUPLICATE",
                    "STUDENT_BIOMETRIC",
                    String.valueOf(student.getId()),
                    "{}",
                    "{}",
                    ex.getMessage()
            );
            throw ex;
        } catch (QualityOrLivenessException ex) {
            ipRateLimiterService.recordEnrollAttempt("ip:" + clientIp);
            ipRateLimiterService.recordEnrollAttempt("account:" + studentId);
            auditChainService.recordEntry(
                    actorId,
                    actorName,
                    "ENROLL_REJECTED_QUALITY/LIVENESS",
                    "STUDENT_BIOMETRIC",
                    String.valueOf(student.getId()),
                    "{}",
                    "{}",
                    ex.getMessage()
            );
            throw new IllegalArgumentException(ex.getMessage(), ex);
        } catch (Exception ex) {
            ipRateLimiterService.recordEnrollAttempt("ip:" + clientIp);
            ipRateLimiterService.recordEnrollAttempt("account:" + studentId);
            auditChainService.recordEntry(
                    actorId,
                    actorName,
                    "ENROLL_REJECTED_QUALITY/LIVENESS",
                    "STUDENT_BIOMETRIC",
                    String.valueOf(student.getId()),
                    "{}",
                    "{}",
                    ex.getMessage()
            );
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    /**
     * Calls AI microservice to extract 512-d ArcFace embedding and head yaw from a single frame byte array.
     */
    public FaceFrameData callAiServiceEnroll(byte[] frameBytes) {
        if (frameBytes == null || frameBytes.length == 0) {
            throw new IllegalArgumentException("Frame bytes cannot be empty.");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(frameBytes) {
                @Override
                public String getFilename() {
                    return "frame.jpg";
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

            double[] emb = new double[512];
            for (int i = 0; i < 512; i++) {
                emb[i] = embeddingNode.get(i).asDouble();
            }

            double yaw = root.has("yaw") ? root.get("yaw").asDouble() : 0.0;
            return new FaceFrameData(emb, yaw);
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
            throw new RuntimeException("Failed to parse AI service response: " + e.getMessage(), e);
        }
    }

    public StudentProfile findStudent(Long studentId) {
        return studentProfileRepository.findById(studentId)
                .or(() -> studentProfileRepository.findByUserId(studentId))
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found for ID: " + studentId));
    }

    private void validateCaptureToken(String tokenStr, Long studentId, String clientIp) {
        if (tokenStr == null || tokenStr.isBlank()) {
            throw new QualityOrLivenessException("Capture token is mandatory.");
        }
        CaptureToken token = captureTokenStore.get(tokenStr.trim());
        if (token == null) {
            throw new QualityOrLivenessException("Invalid capture token.");
        }
        if (Instant.now().isAfter(token.getExpiresAt())) {
            captureTokenStore.remove(tokenStr.trim());
            throw new QualityOrLivenessException("Capture token has expired. Please restart enrollment.");
        }
        if (token.isUsed()) {
            throw new QualityOrLivenessException("Capture token has already been used (single-use enforced).");
        }
        // Mark used immediately
        token.setUsed(true);

        if (!token.getStudentId().equals(studentId)) {
            throw new QualityOrLivenessException("Capture token was issued for a different student account.");
        }
        if (!isIpMatching(token.getClientIp(), clientIp)) {
            throw new QualityOrLivenessException("Capture token IP mismatch.");
        }
    }

    private boolean isIpMatching(String tokenIp, String clientIp) {
        if (tokenIp == null || clientIp == null || tokenIp.isBlank() || clientIp.isBlank()) {
            return true;
        }
        boolean tokenIsLocal = tokenIp.equals("127.0.0.1") || tokenIp.equals("0:0:0:0:0:0:0:1") || tokenIp.equalsIgnoreCase("localhost");
        boolean clientIsLocal = clientIp.equals("127.0.0.1") || clientIp.equals("0:0:0:0:0:0:0:1") || clientIp.equalsIgnoreCase("localhost");
        if (tokenIsLocal && clientIsLocal) return true;
        return tokenIp.equalsIgnoreCase(clientIp);
    }

    private void checkRateLimits(String clientIp, Long studentId) {
        if (!ipRateLimiterService.isEnrollAllowed("ip:" + clientIp)) {
            throw new IllegalStateException("Too many enrollment attempts from this IP address. Please try again after 15 minutes.");
        }
        if (studentId != null && !ipRateLimiterService.isEnrollAllowed("account:" + studentId)) {
            throw new IllegalStateException("Too many enrollment attempts for this student account. Please try again after 15 minutes.");
        }
    }

    private void checkForDuplicateFace(Long studentId, double[] candidateAverageEmbedding) {
        List<StudentProfile> enrolledList = studentProfileRepository.findAll();
        for (StudentProfile other : enrolledList) {
            if (other.getId().equals(studentId)) continue;
            if (!Boolean.TRUE.equals(other.getFaceEnrolled())) continue;
            if (other.getFaceEmbedding() == null || other.getFaceEmbedding().isBlank()) continue;

            double[] otherEmbedding = parseEmbeddingJson(other.getFaceEmbedding());
            if (otherEmbedding == null) continue;

            double similarity = cosineSimilarity(candidateAverageEmbedding, otherEmbedding);
            if (similarity >= faceMatchThreshold) {
                // Anonymized log without student IDs or similarity values
                log.warn("Duplicate face detected during biometric enrollment.");
                throw new DuplicateFaceException("Biometric enrollment rejected: Face template matches an existing enrolled student. Please contact administration.");
            }
        }
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA <= 1e-12 || normB <= 1e-12) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static double[] computeAverageNormalizedEmbedding(double[][] embeddings) {
        if (embeddings == null || embeddings.length == 0) return new double[0];
        int dim = embeddings[0].length;
        double[] avg = new double[dim];
        for (double[] emb : embeddings) {
            for (int i = 0; i < dim; i++) {
                avg[i] += emb[i];
            }
        }
        for (int i = 0; i < dim; i++) {
            avg[i] /= embeddings.length;
        }

        // L2 normalize
        double norm = 0;
        for (int i = 0; i < dim; i++) {
            norm += avg[i] * avg[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-6) {
            for (int i = 0; i < dim; i++) {
                avg[i] /= norm;
            }
        }
        return avg;
    }

    public static byte[] decodeBase64Image(String rawBase64) {
        if (rawBase64 == null || rawBase64.isBlank()) {
            throw new QualityOrLivenessException("Frame payload cannot be empty.");
        }
        String clean = rawBase64.trim();
        if (clean.contains(",")) {
            clean = clean.substring(clean.indexOf(",") + 1);
        }
        try {
            return Base64.getDecoder().decode(clean);
        } catch (IllegalArgumentException e) {
            throw new QualityOrLivenessException("Failed to decode base64 frame: " + e.getMessage(), e);
        }
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private double[] parseEmbeddingJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray() || node.size() != 512) return null;
            double[] arr = new double[512];
            for (int i = 0; i < 512; i++) {
                arr[i] = node.get(i).asDouble();
            }
            return arr;
        } catch (Exception e) {
            log.warn("Failed to parse enrolled embedding JSON.");
            return null;
        }
    }

    private List<Double> toDoubleList(double[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (double v : arr) list.add(v);
        return list;
    }

    public static class DuplicateFaceException extends IllegalArgumentException {
        public DuplicateFaceException(String message) {
            super(message);
        }
    }

    public static class QualityOrLivenessException extends IllegalArgumentException {
        public QualityOrLivenessException(String message) {
            super(message);
        }
        public QualityOrLivenessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}