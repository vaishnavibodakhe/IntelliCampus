package com.intellicampus.attendance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellicampus.academic.model.Classroom;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.model.Subject;
import com.intellicampus.academic.repository.ClassroomRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.academic.repository.SubjectRepository;
import com.intellicampus.attendance.dto.*;
import com.intellicampus.attendance.model.AttendanceRecord;
import com.intellicampus.attendance.model.AttendanceSession;
import com.intellicampus.attendance.model.AttendanceStatus;
import com.intellicampus.attendance.model.SessionPhoto;
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.attendance.repository.AttendanceRecordRepository;
import com.intellicampus.attendance.repository.AttendanceSessionRepository;
import com.intellicampus.attendance.repository.SessionPhotoRepository;
import com.intellicampus.attendance.validation.GeofenceValidator;
import com.intellicampus.attendance.validation.SpoofScoreValidator;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;
import com.intellicampus.user.model.Role;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final SessionPhotoRepository sessionPhotoRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final DivisionRepository divisionRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final GeofenceValidator geofenceValidator;
    private final SpoofScoreValidator spoofScoreValidator;
    private final com.intellicampus.security.util.SecurityUtils securityUtils;

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    @Value("${app.attendance.face-match-threshold:0.50}")
    private double faceMatchThreshold = 0.50;

    @Value("${app.attendance.face-review-threshold:0.20}")
    private double faceReviewThreshold = 0.20;

    @Value("${app.attendance.face-match-margin:0.0}")
    private double faceMatchMargin = 0.0;

    @Value("${app.ai.service-url:http://localhost:8000}")
    private String aiServiceUrl = "http://localhost:8000";

    public AttendanceService(AttendanceSessionRepository sessionRepository,
                             AttendanceRecordRepository recordRepository,
                             SessionPhotoRepository sessionPhotoRepository,
                             UserRepository userRepository,
                             SubjectRepository subjectRepository,
                             DivisionRepository divisionRepository,
                             ClassroomRepository classroomRepository,
                             StudentProfileRepository studentProfileRepository,
                             GeofenceValidator geofenceValidator,
                             SpoofScoreValidator spoofScoreValidator) {
        this(sessionRepository, recordRepository, sessionPhotoRepository, userRepository, subjectRepository, divisionRepository, classroomRepository, studentProfileRepository, geofenceValidator, spoofScoreValidator, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AttendanceService(AttendanceSessionRepository sessionRepository,
                             AttendanceRecordRepository recordRepository,
                             SessionPhotoRepository sessionPhotoRepository,
                             UserRepository userRepository,
                             SubjectRepository subjectRepository,
                             DivisionRepository divisionRepository,
                             ClassroomRepository classroomRepository,
                             StudentProfileRepository studentProfileRepository,
                             GeofenceValidator geofenceValidator,
                             SpoofScoreValidator spoofScoreValidator,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) com.intellicampus.security.util.SecurityUtils securityUtils) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.sessionPhotoRepository = sessionPhotoRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.divisionRepository = divisionRepository;
        this.classroomRepository = classroomRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.geofenceValidator = geofenceValidator;
        this.spoofScoreValidator = spoofScoreValidator;
        this.securityUtils = securityUtils;
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

    public void setFaceReviewThreshold(double faceReviewThreshold) {
        this.faceReviewThreshold = faceReviewThreshold;
    }

    public void setFaceMatchMargin(double faceMatchMargin) {
        this.faceMatchMargin = faceMatchMargin;
    }

    public double getFaceMatchThreshold() {
        return faceMatchThreshold;
    }

    public double getFaceReviewThreshold() {
        return faceReviewThreshold;
    }

    public double getFaceMatchMargin() {
        return faceMatchMargin;
    }

    public void setAiServiceUrl(String aiServiceUrl) {
        this.aiServiceUrl = aiServiceUrl;
    }

    public SessionResponseDto startSession(StartSessionRequestDto req) {
        User caller = (securityUtils != null && securityUtils.getCurrentUser().isPresent())
                ? securityUtils.getCurrentUser().get()
                : null;
        return startSession(req, caller);
    }

    public SessionResponseDto startSession(StartSessionRequestDto req, User caller) {
        Long resolvedFacultyId = req.getFacultyId();
        if (caller != null) {
            if (caller.getRole() == Role.FACULTY) {
                resolvedFacultyId = caller.getId();
            } else if (caller.getRole() != Role.PRINCIPAL && caller.getRole() != Role.HOD) {
                throw new AccessDeniedException("User is not authorized to create attendance sessions.");
            }
        }
        final Long targetFacultyId = resolvedFacultyId;
        if (targetFacultyId == null) {
            throw new IllegalArgumentException("Faculty not found or not specified");
        }

        User faculty = userRepository.findById(targetFacultyId)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + targetFacultyId));
        Subject subject = subjectRepository.findById(req.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + req.getSubjectId()));
        Division division = divisionRepository.findById(req.getDivisionId())
                .orElseThrow(() -> new IllegalArgumentException("Division not found: " + req.getDivisionId()));
        Classroom room = classroomRepository.findById(req.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + req.getRoomId()));

        // Close any previously open session for this faculty
        sessionRepository.findFirstByFacultyAndStatus(faculty, SessionStatus.OPEN).ifPresent(s -> {
            s.setStatus(SessionStatus.EXPIRED);
            s.setFinalisedAt(LocalDateTime.now());
            sessionRepository.save(s);
        });

        String challengeCode = req.getChallengeCode() != null && !req.getChallengeCode().isBlank()
                ? req.getChallengeCode().toUpperCase()
                : generateRandomChallengeCode();

        AttendanceSession session = new AttendanceSession(
                faculty,
                subject,
                division,
                room,
                challengeCode,
                req.getPhotoWindowMinutes()
        );
        session.setSessionNonce(generateSecureNonce());
        int window = req.getPhotoWindowMinutes() != null ? req.getPhotoWindowMinutes() : 10;
        session.setExpiresAt(session.getStartedAt().plusMinutes(window));
        session.setPhotoCount(0);

        AttendanceSession savedSession = sessionRepository.save(session);

        // Populate initial PENDING attendance records for all students in the division
        List<StudentProfile> students = studentProfileRepository.findByDivisionId(division.getId());
        for (StudentProfile st : students) {
            AttendanceRecord record = new AttendanceRecord(
                    savedSession,
                    st,
                    null,
                    null,
                    AttendanceStatus.PENDING
            );
            recordRepository.save(record);
        }

        return getSessionDetails(savedSession.getId());
    }

    public PhotoRecognitionResultDto processClassroomPhoto(
            Long sessionId,
            MultipartFile photo,
            Double latitude,
            Double longitude,
            Double accuracyM) {
        return processClassroomPhoto(sessionId, photo, latitude, longitude, accuracyM, null, null);
    }

    public synchronized PhotoRecognitionResultDto processClassroomPhoto(
            Long sessionId,
            MultipartFile photo,
            Double latitude,
            Double longitude,
            Double accuracyM,
            String sessionToken,
            User caller) {

        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Classroom photo is mandatory.");
        }

        if (photo.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Classroom photo exceeds maximum size limit (10MB).");
        }

        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // Validate legal status
        if (session.getStatus() == SessionStatus.FINALISED) {
            throw new IllegalStateException("Attendance session is finalised and cannot accept photo submissions.");
        }
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Attendance session is not currently open (Status: " + session.getStatus() + ")");
        }

        // Validate expiry
        LocalDateTime now = LocalDateTime.now();
        if ((session.getPhotoWindowEndsAt() != null && now.isAfter(session.getPhotoWindowEndsAt()))
                || (session.getExpiresAt() != null && now.isAfter(session.getExpiresAt()))) {
            throw new IllegalStateException("Classroom photo submission window has expired for this session.");
        }

        // Validate faculty authorization if caller context provided
        if (caller != null) {
            boolean isFaculty = session.getFaculty() != null && caller.getId().equals(session.getFaculty().getId());
            boolean isPrivileged = caller.getRole() == Role.PRINCIPAL || caller.getRole() == Role.HOD;
            if (!isFaculty && !isPrivileged) {
                throw new AccessDeniedException("User is not authorized to submit photos for this attendance session.");
            }
        }

        // Validate session nonce/token if session has one configured
        if (session.getSessionNonce() != null && !session.getSessionNonce().isBlank()) {
            if (sessionToken == null || !session.getSessionNonce().equals(sessionToken.trim())) {
                throw new SecurityException("Invalid or missing session security nonce token.");
            }
        }

        // Enforce 5-photo limit per session
        long photoCount = sessionPhotoRepository.countBySession(session);
        int currentPhotoCount = session.getPhotoCount() != null ? session.getPhotoCount() : 0;
        if (photoCount >= 5 || currentPhotoCount >= 5) {
            throw new IllegalStateException("Maximum limit of 5 photos per session has been reached.");
        }

        byte[] photoBytes;
        try {
            photoBytes = photo.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read classroom photo bytes: " + e.getMessage(), e);
        }

        if (photoBytes.length < 4) {
            throw new IllegalArgumentException("Uploaded photo payload is empty or malformed.");
        }

        Integer imgWidth = null;
        Integer imgHeight = null;
        // For non-synthetic payloads, validate decodability
        if (photoBytes.length > 32) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(photoBytes));
                if (img == null) {
                    throw new IllegalArgumentException("Uploaded file could not be decoded as an image.");
                }
                imgWidth = img.getWidth();
                imgHeight = img.getHeight();
            } catch (IOException e) {
                throw new IllegalArgumentException("Malformed image payload: " + e.getMessage(), e);
            }
        }

        String sha256 = sha256Hex(photoBytes);
        if (sessionPhotoRepository.existsBySessionIdAndSha256(sessionId, sha256)) {
            throw new IllegalArgumentException("This exact photograph has already been uploaded for this session.");
        }

        // Geofence validation - coordinates mandatory; do not silently substitute room coordinates
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("GPS coordinates (latitude and longitude) are mandatory for classroom attendance.");
        }
        double roomLat = session.getRoom() != null && session.getRoom().getLatitude() != null ? session.getRoom().getLatitude() : 0.0;
        double roomLon = session.getRoom() != null && session.getRoom().getLongitude() != null ? session.getRoom().getLongitude() : 0.0;
        Integer roomRadius = session.getRoom() != null ? session.getRoom().getRadiusM() : 50;
        geofenceValidator.validate(latitude, longitude, accuracyM, roomLat, roomLon, roomRadius);

        long phash = computeSimplePHash(photoBytes);
        double photoAcc = accuracyM != null ? accuracyM : 10.0;

        // Atomic database conditional increment for cross-instance concurrency safety
        int updatedCount = sessionRepository.incrementPhotoCountIfUnderLimit(sessionId);
        if (updatedCount == 0) {
            throw new IllegalStateException("Maximum limit of 5 photos per session has been reached.");
        }

        SessionPhoto sessionPhoto = new SessionPhoto(session, sha256, phash, latitude, longitude, photoAcc, 0);
        sessionPhoto = sessionPhotoRepository.save(sessionPhoto);

        // Update session photo count atomically
        session.setPhotoCount((int) Math.max(photoCount, currentPhotoCount) + 1);
        sessionRepository.save(session);

        // Load enrolled students of the division and format candidates
        List<StudentProfile> divisionStudents = studentProfileRepository.findByDivisionId(session.getDivision().getId());
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (StudentProfile s : divisionStudents) {
            if (Boolean.TRUE.equals(s.getFaceEnrolled()) && s.getFaceEmbedding() != null && !s.getFaceEmbedding().isBlank()) {
                try {
                    List<Double> emb = objectMapper.readValue(s.getFaceEmbedding(), new TypeReference<List<Double>>() {});
                    if (emb != null && emb.size() == 512) {
                        Map<String, Object> c = new HashMap<>();
                        c.put("studentId", s.getId());
                        c.put("embedding", emb);
                        candidates.add(c);
                    } else {
                        log.warn("Skipping candidate student {} with invalid embedding dimension: {}", s.getId(), emb != null ? emb.size() : 0);
                    }
                } catch (Exception ex) {
                    log.warn("Could not deserialize embedding for student {}", s.getId(), ex);
                }
            }
        }

        // Call AI service POST /ai/recognize
        AiRecognitionResponse aiResp = callAiServiceRecognize(photoBytes, photo.getOriginalFilename(), candidates);

        sessionPhoto.setFaceCount(aiResp.faceCount);
        sessionPhotoRepository.save(sessionPhoto);

        // Process matches against strict matching rules
        int photoMatched = 0;
        int photoInReview = 0;
        int photoUnmatched = 0;
        List<DetectedFaceDto> detectedFaces = new ArrayList<>();
        Set<Long> assignedStudentsInPhoto = new HashSet<>();
        double effectiveMargin = faceMatchMargin > 0 ? faceMatchMargin : 0.02;

        for (AiFaceResult face : aiResp.faces) {
            // Guard 1: Missing candidate or similarity below REVIEW threshold (0.20)
            // Rule: Similarity < 20%: UNMATCHED; never assign an arbitrary student
            if (face.matchedStudentId == null || face.similarity == null || face.similarity < faceReviewThreshold) {
                photoUnmatched++;
                detectedFaces.add(new DetectedFaceDto(
                        face.faceId,
                        face.bbox,
                        face.quality,
                        null,
                        null,
                        null,
                        face.spoofScore,
                        "UNMATCHED"
                ));
                continue;
            }

            Long studentId = face.matchedStudentId;
            double sim = face.similarity;
            double secondSim = face.secondSimilarity != null ? face.secondSimilarity : 0.0;

            // Guard 2: Strict 1-to-1 matching per photo
            // One detected face cannot be assigned to multiple students, and one student cannot be assigned to multiple faces
            if (assignedStudentsInPhoto.contains(studentId)) {
                log.warn("Duplicate assignment in photo for student {}: face {} rejected as UNMATCHED", studentId, face.faceId);
                photoUnmatched++;
                detectedFaces.add(new DetectedFaceDto(
                        face.faceId,
                        face.bbox,
                        face.quality,
                        null,
                        null,
                        null,
                        face.spoofScore,
                        "UNMATCHED"
                ));
                continue;
            }

            // Find enrolled student profile
            StudentProfile sp = null;
            if (divisionStudents != null) {
                for (StudentProfile s : divisionStudents) {
                    if (studentId.equals(s.getId())) {
                        sp = s;
                        break;
                    }
                }
            }
            if (sp == null) {
                sp = studentProfileRepository.findById(studentId).orElse(null);
                if (sp != null && sp.getDivision() != null && !sp.getDivision().getId().equals(session.getDivision().getId())) {
                    sp = null;
                }
            }

            if (sp == null) {
                photoUnmatched++;
                detectedFaces.add(new DetectedFaceDto(
                        face.faceId,
                        face.bbox,
                        face.quality,
                        null,
                        null,
                        null,
                        face.spoofScore,
                        "UNMATCHED"
                ));
                continue;
            }

            String studentName = sp.getUser() != null ? sp.getUser().getName() : "Student #" + sp.getId();

            // Safeguards:
            // 1. Ambiguous tie: top similarity and runner-up candidate similarity are within effectiveMargin
            boolean isAmbiguousTie = (secondSim > 0 && Math.abs(sim - secondSim) < effectiveMargin);
            // 2. Residual conflict: a competitor candidate had a higher similarity than the assigned candidate
            boolean isResidualConflict = (secondSim > sim);
            // 3. Face quality check
            boolean isQualityOk = (face.quality >= 0.40);

            AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId).orElse(null);
            if (record == null) {
                record = new AttendanceRecord(session, sp, null, null, AttendanceStatus.PENDING);
            }

            boolean isAlreadyPresent = (record.getFinalStatus() == AttendanceStatus.PRESENT);
            boolean isManualOverride = (record.getStatusReason() != null && record.getStatusReason().startsWith("Faculty manual override"));

            if (isAlreadyPresent || isManualOverride) {
                // If student is already confirmed PRESENT (e.g. from an earlier photo):
                // A new face in a subsequent photo is ONLY validly this student if it satisfies high confidence (>= 0.50)
                // Low-confidence / borderline faces (< 0.50) in subsequent photos must NOT be falsely attributed to
                // a student who is already confirmed PRESENT.
                if (sim >= faceMatchThreshold && !isAmbiguousTie && !isResidualConflict && isQualityOk) {
                    assignedStudentsInPhoto.add(studentId);
                    if (record.getFaceScore() == null || sim > record.getFaceScore()) {
                        record.setFaceScore(sim);
                    }
                    record.setSpoofScore(face.spoofScore);
                    recordRepository.save(record);
                    photoMatched++;
                    detectedFaces.add(new DetectedFaceDto(
                            face.faceId,
                            face.bbox,
                            face.quality,
                            studentId,
                            studentName,
                            sim,
                            face.spoofScore,
                            "PRESENT"
                    ));
                } else {
                    // Face does not match the already PRESENT student with required confidence; treat as UNMATCHED
                    // to prevent false identity attribution and prevent contradiction between bounding box and roster.
                    photoUnmatched++;
                    detectedFaces.add(new DetectedFaceDto(
                            face.faceId,
                            face.bbox,
                            face.quality,
                            null,
                            null,
                            null,
                            face.spoofScore,
                            "UNMATCHED"
                    ));
                }
            } else {
                // Student not already PRESENT:
                // Rule 1: Similarity >= 50%: eligible for PRESENT only if valid and passes all safeguards
                if (sim >= faceMatchThreshold && !isAmbiguousTie && !isResidualConflict && isQualityOk) {
                    assignedStudentsInPhoto.add(studentId);
                    record.setFinalStatus(AttendanceStatus.PRESENT);
                    record.setConfirmed(true);
                    record.setConfirmedAt(LocalDateTime.now());
                    record.setFaceScore(sim);
                    record.setSpoofScore(face.spoofScore);
                    record.setStatusReason(String.format("Biometric face recognition verified (Cosine: %.2f >= %.2f)",
                            sim, faceMatchThreshold));
                    recordRepository.save(record);
                    photoMatched++;
                    detectedFaces.add(new DetectedFaceDto(
                            face.faceId,
                            face.bbox,
                            face.quality,
                            studentId,
                            studentName,
                            sim,
                            face.spoofScore,
                            "PRESENT"
                    ));
                } else {
                    // Rule 2: Similarity >= 20% and < 50% (or failed safeguards): REVIEW, never automatically PRESENT
                    assignedStudentsInPhoto.add(studentId);
                    record.setFinalStatus(AttendanceStatus.REVIEW);
                    record.setConfirmed(false);
                    record.setFaceScore(sim);
                    record.setSpoofScore(face.spoofScore);
                    if (isAmbiguousTie || isResidualConflict) {
                        record.setStatusReason(String.format("Ambiguous biometric match: close similarity with multiple candidates (Cosine: %.2f, runner-up: %.2f). Requires faculty verification.",
                                sim, secondSim));
                    } else if (!isQualityOk) {
                        record.setStatusReason(String.format("Low face detection quality (%.2f < 0.40). Requires faculty verification.", face.quality));
                    } else {
                        record.setStatusReason(String.format("Borderline biometric match (Cosine: %.2f in [%.2f, %.2f)). Requires faculty verification.",
                                sim, faceReviewThreshold, faceMatchThreshold));
                    }
                    recordRepository.save(record);
                    photoInReview++;
                    detectedFaces.add(new DetectedFaceDto(
                            face.faceId,
                            face.bbox,
                            face.quality,
                            studentId,
                            studentName,
                            sim,
                            face.spoofScore,
                            "REVIEW"
                    ));
                }
            }
        }

        SessionResponseDto updatedSession = getSessionDetails(sessionId);

        PhotoRecognitionResultDto result = new PhotoRecognitionResultDto();
        result.setSessionId(sessionId);
        result.setPhotoId(sessionPhoto.getId());
        result.setPhotoIndex((int) (photoCount + 1));
        result.setTotalPhotos((int) (photoCount + 1));
        result.setFacesDetected(aiResp.faceCount);
        result.setStudentsMatched(photoMatched);
        result.setStudentsInReview(photoInReview);
        result.setUnmatchedFaceCount(photoUnmatched);
        result.setUnmatchedFaces(photoUnmatched);
        result.setRecords(updatedSession.getRecords());
        result.setDetectedFaces(detectedFaces);
        result.setImageWidth(imgWidth);
        result.setImageHeight(imgHeight);
        result.setMessage(String.format("Photo %d processed: %d faces detected, %d PRESENT, %d in REVIEW.",
                photoCount + 1, aiResp.faceCount, result.getStudentsMatched(), result.getStudentsInReview()));

        return result;
    }

    public SessionResponseDto transitionSessionStatus(Long sessionId, SessionStatus newStatus) {
        return transitionSessionStatus(sessionId, newStatus, null);
    }

    public synchronized SessionResponseDto transitionSessionStatus(Long sessionId, SessionStatus newStatus, User caller) {
        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (caller != null) {
            boolean isFaculty = session.getFaculty() != null && caller.getId().equals(session.getFaculty().getId());
            boolean isPrivileged = caller.getRole() == Role.PRINCIPAL || caller.getRole() == Role.HOD;
            if (!isFaculty && !isPrivileged) {
                throw new AccessDeniedException("User is not authorized to modify this session status.");
            }
        }

        if (session.getStatus() == SessionStatus.FINALISED) {
            throw new IllegalStateException("Attendance session is already finalised and cannot transition to " + newStatus);
        }

        if (session.getStatus() == SessionStatus.EXPIRED && newStatus == SessionStatus.OPEN) {
            throw new IllegalStateException("Expired attendance session cannot be reopened.");
        }

        session.setStatus(newStatus);
        if (newStatus == SessionStatus.FINALISED) {
            session.setFinalisedAt(LocalDateTime.now());
            // Mark unresolved records as ABSENT
            List<AttendanceRecord> records = recordRepository.findBySession(session);
            for (AttendanceRecord r : records) {
                if (r.getFinalStatus() == AttendanceStatus.PENDING || r.getFinalStatus() == AttendanceStatus.MATCHED) {
                    r.setFinalStatus(AttendanceStatus.ABSENT);
                    r.setStatusReason("Student did not verify attendance before session finalisation");
                    recordRepository.save(r);
                }
            }
        }

        AttendanceSession updated = sessionRepository.save(session);
        return getSessionDetails(updated.getId());
    }

    @Transactional(readOnly = true)
    public SessionResponseDto getSessionDetails(Long sessionId) {
        AttendanceSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<AttendanceRecord> records = recordRepository.findBySession(s);

        SessionResponseDto dto = new SessionResponseDto();
        dto.setId(s.getId());
        dto.setFacultyId(s.getFaculty().getId());
        dto.setFacultyName(s.getFaculty().getName());
        dto.setSubjectId(s.getSubject().getId());
        dto.setSubjectCode(s.getSubject().getCode());
        dto.setSubjectName(s.getSubject().getName());
        dto.setRoomId(s.getRoom().getId());
        dto.setRoomName(s.getRoom().getName());
        dto.setDivisionId(s.getDivision().getId());
        dto.setDivisionName(s.getDivision().getName());
        dto.setChallengeCode(s.getChallengeCode());
        dto.setSessionNonce(s.getSessionNonce());
        dto.setPhotoCount(s.getPhotoCount() != null ? s.getPhotoCount() : 0);
        dto.setExpiresAt(s.getExpiresAt());
        dto.setStatus(s.getStatus());
        dto.setStartedAt(s.getStartedAt());
        dto.setPhotoWindowEndsAt(s.getPhotoWindowEndsAt());
        dto.setFinalisedAt(s.getFinalisedAt());

        long present = records.stream().filter(r -> r.getFinalStatus() == AttendanceStatus.PRESENT).count();
        long matched = records.stream().filter(r -> r.getFinalStatus() == AttendanceStatus.MATCHED).count();
        long review = records.stream().filter(r -> r.getFinalStatus() == AttendanceStatus.REVIEW).count();
        long absent = records.stream().filter(r -> r.getFinalStatus() == AttendanceStatus.ABSENT).count();

        dto.setTotalPresent(present);
        dto.setTotalMatched(matched);
        dto.setTotalReview(review);
        dto.setTotalAbsent(absent);

        dto.setRecords(records.stream().map(this::mapRecordToDto).collect(Collectors.toList()));
        return dto;
    }

    public AttendanceRecordResponseDto overrideRecordStatus(Long sessionId, Long studentId, ManualOverrideDto override) {
        return overrideRecordStatus(sessionId, studentId, override, null);
    }

    public synchronized AttendanceRecordResponseDto overrideRecordStatus(Long sessionId, Long studentId, ManualOverrideDto override, User caller) {
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance record not found for student " + studentId));

        AttendanceSession session = record.getSession() != null
                ? record.getSession()
                : sessionRepository.findById(sessionId).orElse(null);

        if (session != null) {
            if (session.getStatus() == SessionStatus.FINALISED) {
                throw new IllegalStateException("Cannot override records for an already finalised session.");
            }
            if (caller != null) {
                boolean isFaculty = session.getFaculty() != null && caller.getId().equals(session.getFaculty().getId());
                boolean isPrivileged = caller.getRole() == Role.PRINCIPAL || caller.getRole() == Role.HOD;
                if (!isFaculty && !isPrivileged) {
                    throw new AccessDeniedException("User is not authorized to override attendance records for this session.");
                }
            }
        }

        record.setFinalStatus(override.getStatus());
        if (override.getStatus() == AttendanceStatus.PRESENT) {
            record.setConfirmed(true);
            record.setConfirmedAt(LocalDateTime.now());
        } else {
            record.setConfirmed(false);
        }
        record.setStatusReason(override.getReason() != null ? "Faculty manual override: " + override.getReason() : "Faculty manual override");
        AttendanceRecord saved = recordRepository.save(record);
        return mapRecordToDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<SessionResponseDto> getActiveSessionForDivision(Long divisionId) {
        return sessionRepository.findFirstByDivisionIdAndStatus(divisionId, SessionStatus.OPEN)
                .map(s -> getSessionDetails(s.getId()));
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponseDto> getRecordsForStudent(Long studentId) {
        return getRecordsForStudent(studentId, null);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponseDto> getRecordsForStudent(Long studentId, User caller) {
        if (caller != null && caller.getRole() == Role.STUDENT) {
            StudentProfile profile = studentProfileRepository.findByUserId(caller.getId())
                    .orElseThrow(() -> new AccessDeniedException("Student profile not found for user: " + caller.getId()));

            boolean matchesUserId = caller.getId().equals(studentId);
            boolean matchesProfileId = profile.getId().equals(studentId);
            if (!matchesUserId && !matchesProfileId) {
                throw new AccessDeniedException("Students are only permitted to view their own attendance records.");
            }
            return recordRepository.findByStudentOrderByCreatedAtDesc(profile)
                    .stream()
                    .map(this::mapRecordToDto)
                    .collect(Collectors.toList());
        }

        StudentProfile student = studentProfileRepository.findById(studentId)
                .or(() -> studentProfileRepository.findByUserId(studentId))
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        if (securityUtils != null) {
            securityUtils.verifyCanAccessStudent(student);
        }

        return recordRepository.findByStudentOrderByCreatedAtDesc(student)
                .stream()
                .map(this::mapRecordToDto)
                .collect(Collectors.toList());
    }

    private AttendanceRecordResponseDto mapRecordToDto(AttendanceRecord r) {
        AttendanceRecordResponseDto dto = new AttendanceRecordResponseDto();
        dto.setId(r.getId());
        if (r.getStudent() != null) {
            dto.setStudentId(r.getStudent().getId());
            if (r.getStudent().getUser() != null) {
                dto.setStudentName(r.getStudent().getUser().getName());
            }
            dto.setRollNo(r.getStudent().getRollNo());
        }
        dto.setFaceScore(r.getFaceScore());
        dto.setSpoofScore(r.getSpoofScore());
        dto.setConfirmed(r.getConfirmed());
        dto.setConfirmedAt(r.getConfirmedAt());
        dto.setFinalStatus(r.getFinalStatus());
        dto.setStatusReason(r.getStatusReason());
        if (r.getSession() != null) {
            dto.setSessionId(r.getSession().getId());
            dto.setSessionStatus(r.getSession().getStatus());
            dto.setSessionDate(r.getSession().getStartedAt());
            if (r.getSession().getSubject() != null) {
                dto.setSubjectId(r.getSession().getSubject().getId());
                dto.setSubjectCode(r.getSession().getSubject().getCode());
                dto.setSubjectName(r.getSession().getSubject().getName());
            }
        }
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String generateRandomChallengeCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(Arrays.hashCode(bytes));
        }
    }

    private long computeSimplePHash(byte[] bytes) {
        long h = 1125899906842597L;
        for (int i = 0; i < bytes.length; i += 16) {
            h = 31 * h + bytes[i];
        }
        return Math.abs(h);
    }

    private AiRecognitionResponse callAiServiceRecognize(byte[] photoBytes, String filename, List<Map<String, Object>> candidates) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(photoBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "classroom.jpg";
                }
            };
            body.add("image", resource);
            body.add("candidates", objectMapper.writeValueAsString(candidates));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            String url = aiServiceUrl + "/ai/recognize";

            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("AI service error during recognition: " + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            AiRecognitionResponse resp = new AiRecognitionResponse();
            resp.faceCount = root.has("faceCount") ? root.get("faceCount").asInt() : 0;
            resp.matchedCount = root.has("matchedCount") ? root.get("matchedCount").asInt() : 0;
            resp.faces = new ArrayList<>();

            if (root.has("faces") && root.get("faces").isArray()) {
                for (JsonNode fNode : root.get("faces")) {
                    AiFaceResult f = new AiFaceResult();
                    f.faceId = fNode.has("faceId") ? fNode.get("faceId").asInt() : 0;
                    f.quality = fNode.has("quality") ? fNode.get("quality").asDouble() : 0.0;
                    if (fNode.has("bbox") && fNode.get("bbox").isArray()) {
                        List<Integer> bbox = new ArrayList<>();
                        for (JsonNode b : fNode.get("bbox")) {
                            bbox.add(b.asInt());
                        }
                        f.bbox = bbox;
                    }
                    f.matchedStudentId = fNode.has("matchedStudentId") && !fNode.get("matchedStudentId").isNull()
                            ? fNode.get("matchedStudentId").asLong()
                            : null;
                    f.similarity = fNode.has("similarity") && !fNode.get("similarity").isNull()
                            ? fNode.get("similarity").asDouble()
                            : null;
                    f.secondSimilarity = fNode.has("secondSimilarity") && !fNode.get("secondSimilarity").isNull()
                            ? fNode.get("secondSimilarity").asDouble()
                            : null;
                    f.spoofScore = fNode.has("spoofScore") && !fNode.get("spoofScore").isNull()
                            ? fNode.get("spoofScore").asDouble()
                            : null;
                    resp.faces.add(f);
                }
            }
            return resp;
        } catch (Exception e) {
            log.error("AI service recognition call failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI recognition service unavailable: " + e.getMessage(), e);
        }
    }

    public static String generateSecureNonce() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class AiRecognitionResponse {
        int faceCount;
        int matchedCount;
        List<AiFaceResult> faces = new ArrayList<>();
    }

    private static class AiFaceResult {
        int faceId;
        List<Integer> bbox;
        double quality;
        Long matchedStudentId;
        Double similarity;
        Double secondSimilarity;
        Double spoofScore;
    }
}