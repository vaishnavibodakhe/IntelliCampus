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

import java.io.IOException;
import java.security.MessageDigest;
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

    @Value("${app.attendance.face-match-threshold:0.45}")
    private double faceMatchThreshold = 0.45;

    @Value("${app.attendance.face-match-margin:0.05}")
    private double faceMatchMargin = 0.05;

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

    public void setFaceMatchMargin(double faceMatchMargin) {
        this.faceMatchMargin = faceMatchMargin;
    }

    public void setAiServiceUrl(String aiServiceUrl) {
        this.aiServiceUrl = aiServiceUrl;
    }

    public SessionResponseDto startSession(StartSessionRequestDto req) {
        User faculty = null;
        if (securityUtils != null && securityUtils.getCurrentUser().isPresent()) {
            User current = securityUtils.getCurrentUser().get();
            if (current.getRole() == com.intellicampus.user.model.Role.FACULTY) {
                faculty = current;
            }
        }
        if (faculty == null && req.getFacultyId() != null) {
            faculty = userRepository.findById(req.getFacultyId()).orElse(null);
        }
        if (faculty == null) {
            throw new IllegalArgumentException("Faculty not found or not specified");
        }
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

        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Classroom photo is mandatory.");
        }

        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Attendance session is not currently open (Status: " + session.getStatus() + ")");
        }

        if (session.getPhotoWindowEndsAt() != null && LocalDateTime.now().isAfter(session.getPhotoWindowEndsAt())) {
            throw new IllegalStateException("Classroom photo submission window has expired for this session.");
        }

        // Limit up to 5 photos per session
        long photoCount = sessionPhotoRepository.countBySession(session);
        if (photoCount >= 5) {
            throw new IllegalStateException("Maximum limit of 5 photos per session has been reached.");
        }

        byte[] photoBytes;
        try {
            photoBytes = photo.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read classroom photo bytes: " + e.getMessage(), e);
        }

        String sha256 = sha256Hex(photoBytes);
        if (sessionPhotoRepository.existsBySessionIdAndSha256(sessionId, sha256)) {
            throw new IllegalArgumentException("This exact photograph has already been uploaded for this session.");
        }

        long phash = computeSimplePHash(photoBytes);
        double photoLat = latitude != null ? latitude : session.getRoom().getLatitude();
        double photoLon = longitude != null ? longitude : session.getRoom().getLongitude();
        double photoAcc = accuracyM != null ? accuracyM : 10.0;

        SessionPhoto sessionPhoto = new SessionPhoto(session, sha256, phash, photoLat, photoLon, photoAcc, 0);
        sessionPhoto = sessionPhotoRepository.save(sessionPhoto);

        // Load enrolled students of the division and format candidates
        List<StudentProfile> divisionStudents = studentProfileRepository.findByDivisionId(session.getDivision().getId());
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (StudentProfile s : divisionStudents) {
            if (Boolean.TRUE.equals(s.getFaceEnrolled()) && s.getFaceEmbedding() != null && !s.getFaceEmbedding().isBlank()) {
                try {
                    List<Double> emb = objectMapper.readValue(s.getFaceEmbedding(), new TypeReference<List<Double>>() {});
                    Map<String, Object> c = new HashMap<>();
                    c.put("studentId", s.getId());
                    c.put("embedding", emb);
                    candidates.add(c);
                } catch (Exception ex) {
                    log.warn("Could not deserialize embedding for student {}", s.getId(), ex);
                }
            }
        }

        // Call AI service POST /ai/recognize
        AiRecognitionResponse aiResp = callAiServiceRecognize(photoBytes, photo.getOriginalFilename(), candidates);

        sessionPhoto.setFaceCount(aiResp.faceCount);
        sessionPhotoRepository.save(sessionPhoto);

        // Process matches against thresholds
        int newlyMatched = 0;
        int newlyInReview = 0;
        int unmatchedCount = 0;

        for (AiFaceResult face : aiResp.faces) {
            if (face.matchedStudentId != null && face.similarity != null) {
                Long studentId = face.matchedStudentId;
                double sim = face.similarity;
                double secondSim = face.secondSimilarity != null ? face.secondSimilarity : 0.0;
                double margin = sim - secondSim;

                AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                        .orElseGet(() -> {
                            StudentProfile sp = studentProfileRepository.findById(studentId).orElse(null);
                            return sp != null ? new AttendanceRecord(session, sp, null, null, AttendanceStatus.PENDING) : null;
                        });

                if (record == null) {
                    continue;
                }

                // Rule: Never downgrade a record already PRESENT or manually overridden
                if (record.getFinalStatus() == AttendanceStatus.PRESENT) {
                    if (record.getFaceScore() == null || sim > record.getFaceScore()) {
                        record.setFaceScore(sim);
                    }
                    record.setSpoofScore(null);
                    recordRepository.save(record);
                } else if (record.getStatusReason() != null && record.getStatusReason().startsWith("Faculty manual override")) {
                    if (record.getFaceScore() == null || sim > record.getFaceScore()) {
                        record.setFaceScore(sim);
                    }
                    record.setSpoofScore(null);
                    recordRepository.save(record);
                } else {
                    // Threshold logic:
                    // similarity >= threshold AND margin >= faceMatchMargin => PRESENT
                    // between (threshold - 0.10) and threshold => REVIEW
                    // otherwise unmatched
                    if (sim >= faceMatchThreshold && margin >= faceMatchMargin) {
                        record.setFinalStatus(AttendanceStatus.PRESENT);
                        record.setConfirmed(true);
                        record.setConfirmedAt(LocalDateTime.now());
                        record.setFaceScore(sim);
                        record.setSpoofScore(null);
                        record.setStatusReason(String.format("Biometric face recognition verified (Cosine: %.2f >= %.2f, Margin: %.2f)", sim, faceMatchThreshold, margin));
                        recordRepository.save(record);
                        newlyMatched++;
                    } else if (sim >= (faceMatchThreshold - 0.10)) {
                        record.setFinalStatus(AttendanceStatus.REVIEW);
                        record.setFaceScore(sim);
                        record.setSpoofScore(null);
                        record.setStatusReason(String.format("Borderline biometric match (Cosine: %.2f in [%.2f, %.2f]). Requires faculty verification.", sim, faceMatchThreshold - 0.10, faceMatchThreshold));
                        recordRepository.save(record);
                        newlyInReview++;
                    } else {
                        unmatchedCount++;
                    }
                }
            } else {
                unmatchedCount++;
            }
        }

        SessionResponseDto updatedSession = getSessionDetails(sessionId);

        PhotoRecognitionResultDto result = new PhotoRecognitionResultDto();
        result.setSessionId(sessionId);
        result.setPhotoId(sessionPhoto.getId());
        result.setPhotoIndex((int) (photoCount + 1));
        result.setTotalPhotos((int) (photoCount + 1));
        result.setFacesDetected(aiResp.faceCount);
        result.setStudentsMatched((int) recordRepository.countBySessionAndFinalStatus(session, AttendanceStatus.PRESENT));
        result.setStudentsInReview((int) recordRepository.countBySessionAndFinalStatus(session, AttendanceStatus.REVIEW));
        result.setUnmatchedFaceCount(unmatchedCount);
        result.setRecords(updatedSession.getRecords());
        result.setMessage(String.format("Photo %d processed: %d faces detected, %d PRESENT, %d in REVIEW.",
                photoCount + 1, aiResp.faceCount, result.getStudentsMatched(), result.getStudentsInReview()));

        return result;
    }

    public SessionResponseDto transitionSessionStatus(Long sessionId, SessionStatus newStatus) {
        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

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
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance record not found for student " + studentId));

        record.setFinalStatus(override.getStatus());
        if (override.getStatus() == AttendanceStatus.PRESENT) {
            record.setConfirmed(true);
            record.setConfirmedAt(LocalDateTime.now());
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
        dto.setStudentId(r.getStudent().getId());
        dto.setStudentName(r.getStudent().getUser().getName());
        dto.setRollNo(r.getStudent().getRollNo());
        dto.setFaceScore(r.getFaceScore());
        dto.setSpoofScore(r.getSpoofScore());
        dto.setConfirmed(r.getConfirmed());
        dto.setConfirmedAt(r.getConfirmedAt());
        dto.setFinalStatus(r.getFinalStatus());
        dto.setStatusReason(r.getStatusReason());
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
                    f.matchedStudentId = fNode.has("matchedStudentId") && !fNode.get("matchedStudentId").isNull()
                            ? fNode.get("matchedStudentId").asLong()
                            : null;
                    f.similarity = fNode.has("similarity") && !fNode.get("similarity").isNull()
                            ? fNode.get("similarity").asDouble()
                            : null;
                    f.secondSimilarity = fNode.has("secondSimilarity") && !fNode.get("secondSimilarity").isNull()
                            ? fNode.get("secondSimilarity").asDouble()
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

    private static class AiRecognitionResponse {
        int faceCount;
        int matchedCount;
        List<AiFaceResult> faces = new ArrayList<>();
    }

    private static class AiFaceResult {
        int faceId;
        double quality;
        Long matchedStudentId;
        Double similarity;
        Double secondSimilarity;
    }
}