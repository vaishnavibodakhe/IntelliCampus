package com.intellicampus.attendance;

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
import com.intellicampus.attendance.service.AttendanceService;
import com.intellicampus.attendance.validation.GeofenceValidator;
import com.intellicampus.attendance.validation.SpoofScoreValidator;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.intellicampus.attendance.validation.GeofenceValidationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for AttendanceService covering face recognition attendance,
 * threshold evaluation, duplicate-match handling, session lifecycle, and manual overrides.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceSessionRepository sessionRepository;
    @Mock
    private AttendanceRecordRepository recordRepository;
    @Mock
    private SessionPhotoRepository sessionPhotoRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private DivisionRepository divisionRepository;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private GeofenceValidator geofenceValidator;
    @Mock
    private SpoofScoreValidator spoofScoreValidator;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AttendanceService attendanceService;

    private User facultyUser;
    private User studentUser;
    private StudentProfile studentProfile;
    private Subject subject;
    private Division division;
    private Classroom classroom;
    private AttendanceSession openSession;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        attendanceService.setRestTemplate(restTemplate);
        attendanceService.setObjectMapper(objectMapper);
        attendanceService.setFaceMatchThreshold(0.50);
        attendanceService.setFaceReviewThreshold(0.20);
        attendanceService.setFaceMatchMargin(0.0);
        lenient().when(spoofScoreValidator.getSpoofThreshold()).thenReturn(0.60);

        facultyUser = new User("Dr. Ramesh Joshi", "ramesh@intellicampus.edu", "pass123", Role.FACULTY, "FAC-001");
        ReflectionTestUtils.setField(facultyUser, "id", 1L);

        studentUser = new User("Aarav Mehta", "aarav@intellicampus.edu", "pass123", Role.STUDENT, "STU-101");
        ReflectionTestUtils.setField(studentUser, "id", 2L);

        studentProfile = new StudentProfile(studentUser, "2024-CS-042");
        studentProfile.setFaceEnrolled(true);
        // 512-d unit vector JSON
        List<Double> emb = new ArrayList<>();
        for (int i = 0; i < 512; i++) emb.add(i == 0 ? 1.0 : 0.0);
        try {
            studentProfile.setFaceEmbedding(objectMapper.writeValueAsString(emb));
        } catch (Exception ignored) {}
        ReflectionTestUtils.setField(studentProfile, "id", 10L);

        subject = new Subject("CS301", "Distributed Systems", 4);
        ReflectionTestUtils.setField(subject, "id", 5L);

        division = new Division("TE-CS-A", 2024);
        ReflectionTestUtils.setField(division, "id", 3L);

        classroom = new Classroom("Lab 301", "Building C", 18.52043, 73.85674, 50);
        ReflectionTestUtils.setField(classroom, "id", 7L);

        openSession = new AttendanceSession(facultyUser, subject, division, classroom, "K7Q2", 10);
        ReflectionTestUtils.setField(openSession, "id", 100L);

        lenient().when(sessionRepository.incrementPhotoCountIfUnderLimit(anyLong())).thenReturn(1);
    }

    @Nested
    @DisplayName("Face Recognition Attendance & Matching Tests")
    class FaceRecognitionTests {

        @Test
        @DisplayName("Criterion 1: Cosine similarity >= 0.50 marks student PRESENT")
        void testRecognitionMatchPresent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.72," +
                    "  \"secondSimilarity\": 0.20," +
                    "  \"spoofScore\": 0.95" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(1, result.getFacesDetected());
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());
            assertTrue(record.getConfirmed());
            assertEquals(0.72, record.getFaceScore());
            assertEquals(0.95, record.getSpoofScore());
            assertTrue(record.getStatusReason().contains("Biometric face recognition verified"));
        }

        @Test
        @DisplayName("Criterion 1b: Null spoofScore with similarity >= 0.50 automatically marks PRESENT without requiring liveness")
        void testNullSpoofScoreWithHighSimilarityMarksPresent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.72," +
                    "  \"secondSimilarity\": 0.20," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(1, result.getFacesDetected());
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());
            assertTrue(record.getConfirmed());
            assertNull(record.getSpoofScore());
            assertEquals(0.72, record.getFaceScore());
            assertTrue(record.getStatusReason().contains("Biometric face recognition verified"));
            assertFalse(record.getStatusReason().contains("liveness verified"));
        }

        @Test
        @DisplayName("Criterion 2: Borderline similarity in [0.20, 0.50) flags student for REVIEW")
        void testRecognitionBorderlineReview() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{5, 6, 7, 8});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.90," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.38," +
                    "  \"secondSimilarity\": 0.15," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.REVIEW, record.getFinalStatus());
            assertEquals(0.38, record.getFaceScore());
            assertTrue(record.getStatusReason().contains("Borderline biometric match"));
        }

        @Test
        @DisplayName("Criterion: One detected face with no enrolled candidates in division counts as Unmatched with bounding box")
        void testOneDetectedFaceNoEnrolledCandidatesInDivision() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class_unmatched.jpg", "image/jpeg", new byte[]{13, 14, 15, 16});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

            // No enrolled candidates in the division
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of());

            // AI service returns 1 detected face, not matched to any student, with a bounding box
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 0," +
                    "\"unmatchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"bbox\": [100, 150, 300, 450]," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": null," +
                    "  \"similarity\": null," +
                    "  \"secondSimilarity\": null," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            when(recordRepository.findBySession(openSession)).thenReturn(List.of());

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(1, result.getFacesDetected());
            assertEquals(0, result.getStudentsMatched());
            assertEquals(0, result.getStudentsInReview());
            assertEquals(1, result.getUnmatchedFaceCount());
            assertEquals(1, result.getUnmatchedFaces());
            assertEquals(result.getFacesDetected(), result.getStudentsMatched() + result.getStudentsInReview() + result.getUnmatchedFaces());

            assertNotNull(result.getDetectedFaces());
            assertEquals(1, result.getDetectedFaces().size());
            DetectedFaceDto face = result.getDetectedFaces().get(0);
            assertEquals("UNMATCHED", face.getStatus());
            assertEquals(List.of(100, 150, 300, 450), face.getBbox());
            assertNull(face.getMatchedStudentId());
        }

        @Test
        @DisplayName("1 unknown face + a roster with 2 PRESENT students must show Matched 0, Review 0, Unmatched 1")
        void testOneUnknownFaceWithTwoPresentStudentsInRosterShowsMatchedZeroReviewZeroUnmatchedOne() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class_unknown.jpg", "image/jpeg", new byte[]{21, 22, 23, 24});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(1L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

            // Roster in DB already has 2 PRESENT records from prior photo or manual override
            User user2 = new User("Rohan Sharma", "rohan@campus.edu", "pass123", Role.STUDENT, "STU-102");
            ReflectionTestUtils.setField(user2, "id", 3L);
            StudentProfile profile2 = new StudentProfile(user2, "2024-CS-043");
            ReflectionTestUtils.setField(profile2, "id", 20L);

            AttendanceRecord present1 = new AttendanceRecord(openSession, studentProfile, 0.85, 0.90, AttendanceStatus.PRESENT);
            present1.setConfirmed(true);
            AttendanceRecord present2 = new AttendanceRecord(openSession, profile2, 0.80, 0.90, AttendanceStatus.PRESENT);
            present2.setConfirmed(true);

            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile, profile2));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(present1, present2));

            // Current photo recognition response: 1 detected face of an unregistered/unknown person
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 0," +
                    "\"unmatchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"bbox\": [110, 90, 250, 310]," +
                    "  \"quality\": 0.94," +
                    "  \"matchedStudentId\": null," +
                    "  \"similarity\": null," +
                    "  \"secondSimilarity\": null," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(1, result.getFacesDetected());
            assertEquals(0, result.getStudentsMatched(), "Current photo matched must be 0, not cumulative session count (2)");
            assertEquals(0, result.getStudentsInReview(), "Current photo review must be 0");
            assertEquals(1, result.getUnmatchedFaces(), "Current photo unmatched must be 1");
            assertEquals(1, result.getUnmatchedFaceCount());
            assertEquals(result.getFacesDetected(), result.getStudentsMatched() + result.getStudentsInReview() + result.getUnmatchedFaces());

            // Roster still preserves the 2 PRESENT students
            assertEquals(2, result.getRecords().size());
            assertTrue(result.getRecords().stream().allMatch(r -> r.getFinalStatus() == AttendanceStatus.PRESENT));
        }

        @Test
        @DisplayName("Criterion 3: Never downgrade an already PRESENT record on subsequent photo uploads")
        void testNeverDowngradeAlreadyPresent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class2.jpg", "image/jpeg", new byte[]{9, 10, 11, 12});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(1L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // In second photo, match similarity is lower (0.36) or borderline
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.85," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.36," +
                    "  \"secondSimilarity\": 0.10," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord alreadyPresentRecord = new AttendanceRecord(openSession, studentProfile, 0.75, null, AttendanceStatus.PRESENT);
            alreadyPresentRecord.setConfirmed(true);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(alreadyPresentRecord));
            lenient().when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(alreadyPresentRecord));

            attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            // Record must remain PRESENT!
            assertEquals(AttendanceStatus.PRESENT, alreadyPresentRecord.getFinalStatus());
            assertEquals(0.75, alreadyPresentRecord.getFaceScore());
        }

        @Test
        @DisplayName("Criterion 4: Reject photo upload when 5 photos limit reached for session")
        void testPhotoLimitEnforced() {
            MockMultipartFile photo = new MockMultipartFile("photo", "class6.jpg", "image/jpeg", new byte[]{1, 2});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(5L);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0));
            assertTrue(ex.getMessage().contains("Maximum limit of 5 photos"));
        }

        @Test
        @DisplayName("Security: Multi-instance concurrent photo limit enforced by database atomic increment")
        void testMultiInstanceConcurrentPhotoLimitEnforced() {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(4L);
            openSession.setPhotoCount(4);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            // Simulate another instance concurrently incremented to 5, so database atomic update updates 0 rows:
            when(sessionRepository.incrementPhotoCountIfUnderLimit(100L)).thenReturn(0);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0));
            assertTrue(ex.getMessage().contains("Maximum limit of 5 photos"));
        }

        @Test
        @DisplayName("Criterion 5: Reject photo upload when photo window expired")
        void testPhotoWindowExpired() {
            openSession.setPhotoWindowEndsAt(LocalDateTime.now().minusMinutes(5));
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0));
            assertTrue(ex.getMessage().contains("expired"));
        }

        @Test
        @DisplayName("Unknown or invalid face never becomes PRESENT")
        void testUnknownFaceNeverMarkedPresent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 0," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.90," +
                    "  \"matchedStudentId\": null," +
                    "  \"similarity\": null," +
                    "  \"secondSimilarity\": null," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(1, result.getFacesDetected());
            assertEquals(1, result.getUnmatchedFaceCount());
            assertEquals(AttendanceStatus.PENDING, record.getFinalStatus());
            assertFalse(Boolean.TRUE.equals(record.getConfirmed()));
        }

        @Test
        @DisplayName("Multiple faces in one photo: detects multiple faces and assigns respective statuses")
        void testMultipleFacesInOnePhoto() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));

            User student2User = new User("Priya Sharma", "priya@intellicampus.edu", "pass123", Role.STUDENT, "STU-102");
            ReflectionTestUtils.setField(student2User, "id", 3L);
            StudentProfile student2Profile = new StudentProfile(student2User, "2024-CS-043");
            ReflectionTestUtils.setField(student2Profile, "id", 11L);

            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile, student2Profile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 3," +
                    "\"matchedCount\": 2," +
                    "\"faces\": [" +
                    "  {\"faceId\": 1, \"quality\": 0.95, \"matchedStudentId\": 10, \"similarity\": 0.75, \"secondSimilarity\": 0.20, \"spoofScore\": 0.95}," +
                    "  {\"faceId\": 2, \"quality\": 0.90, \"matchedStudentId\": 11, \"similarity\": 0.40, \"secondSimilarity\": 0.10, \"spoofScore\": 0.90}," +
                    "  {\"faceId\": 3, \"quality\": 0.85, \"matchedStudentId\": null, \"similarity\": null, \"secondSimilarity\": null, \"spoofScore\": null}" +
                    "]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord rec1 = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            AttendanceRecord rec2 = new AttendanceRecord(openSession, student2Profile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(rec1));
            when(recordRepository.findBySessionIdAndStudentId(100L, 11L)).thenReturn(Optional.of(rec2));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(rec1, rec2));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(3, result.getFacesDetected());
            assertEquals(1, result.getUnmatchedFaceCount());
            assertEquals(AttendanceStatus.PRESENT, rec1.getFinalStatus());
            assertEquals(AttendanceStatus.REVIEW, rec2.getFinalStatus());
        }

        @Test
        @DisplayName("Same student detected across multiple photos updates existing record without duplicates")
        void testSameStudentAcrossMultiplePhotosNoDuplicateRecords() throws Exception {
            MockMultipartFile photo1 = new MockMultipartFile("photo", "class1.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
            MockMultipartFile photo2 = new MockMultipartFile("photo", "class2.jpg", "image/jpeg", new byte[]{5, 6, 7, 8});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L, 1L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{\"faceId\": 1, \"quality\": 0.95, \"matchedStudentId\": 10, \"similarity\": 0.70, \"secondSimilarity\": 0.15, \"spoofScore\": 0.92}]" +
                    "}";
            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            attendanceService.processClassroomPhoto(100L, photo1, 18.52043, 73.85674, 10.0);
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());

            // Second photo with higher score
            String aiResponseJson2 = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{\"faceId\": 1, \"quality\": 0.98, \"matchedStudentId\": 10, \"similarity\": 0.88, \"secondSimilarity\": 0.15, \"spoofScore\": 0.96}]" +
                    "}";
            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson2, HttpStatus.OK));

            attendanceService.processClassroomPhoto(100L, photo2, 18.52043, 73.85674, 10.0);

            // Record is updated in place, remaining PRESENT with updated faceScore
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());
            assertEquals(0.88, record.getFaceScore());
        }

        @Test
        @DisplayName("Reject replayed or exact duplicate photo submission")
        void testExactDuplicatePhotoRejection() {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(1L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0));
            assertTrue(ex.getMessage().contains("already been uploaded"));
        }

        @Test
        @DisplayName("Reject photo upload when session nonce/token is invalid or missing")
        void testInvalidSessionNonceRejection() {
            openSession.setSessionNonce("VALID_NONCE_SECRET_123");
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            // Missing nonce
            assertThrows(SecurityException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0, null, null));

            // Wrong nonce
            assertThrows(SecurityException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0, "WRONG_NONCE", null));
        }

        @Test
        @DisplayName("Anti-spoof check does not override similarity rules: >= 0.50 marks student PRESENT even if spoofScore is low or null")
        void testAntiSpoofDoesNotOverrideSimilarityRule() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // Low spoofScore (0.40) does not prevent qualifying similarity 0.85 from becoming PRESENT
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.85," +
                    "  \"secondSimilarity\": 0.10," +
                    "  \"spoofScore\": 0.40" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());
            assertTrue(record.getConfirmed());
            assertEquals(0.85, record.getFaceScore());
            assertEquals(0.40, record.getSpoofScore());
            assertTrue(record.getStatusReason().contains("Biometric face recognition verified"));
            assertFalse(record.getStatusReason().contains("liveness verified"));
        }

        @Test
        @DisplayName("Exact boundary 0.1999 => UNMATCHED / LOW_CONFIDENCE with no student identity assigned")
        void testBoundary1999IsUnmatchedWithoutIdentityAttribution() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.90," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.1999," +
                    "  \"secondSimilarity\": 0.10," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(1, result.getFacesDetected());
            assertEquals(0, result.getStudentsMatched());
            assertEquals(0, result.getStudentsInReview());
            assertEquals(1, result.getUnmatchedFaces());
            assertEquals(1, result.getUnmatchedFaceCount());

            assertEquals(AttendanceStatus.PENDING, record.getFinalStatus());
            assertFalse(Boolean.TRUE.equals(record.getConfirmed()));

            DetectedFaceDto faceDto = result.getDetectedFaces().get(0);
            assertEquals("UNMATCHED", faceDto.getStatus());
            assertNull(faceDto.getMatchedStudentId());
            assertNull(faceDto.getStudentName());
        }

        @Test
        @DisplayName("Exact boundary 0.20 => REVIEW")
        void testBoundary20RoutesToReview() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.90," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.20," +
                    "  \"secondSimilarity\": 0.10," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.REVIEW, record.getFinalStatus());
            assertEquals(1, result.getStudentsInReview());
            assertEquals(0, result.getStudentsMatched());
            assertEquals(0, result.getUnmatchedFaces());
            assertEquals("REVIEW", result.getDetectedFaces().get(0).getStatus());
            assertEquals(Long.valueOf(10L), result.getDetectedFaces().get(0).getMatchedStudentId());
        }

        @Test
        @DisplayName("Exact boundary 0.4999 => REVIEW")
        void testBoundary4999RoutesToReview() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.90," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.4999," +
                    "  \"secondSimilarity\": 0.20," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.REVIEW, record.getFinalStatus());
            assertEquals(1, result.getStudentsInReview());
            assertEquals(0, result.getStudentsMatched());
            assertEquals("REVIEW", result.getDetectedFaces().get(0).getStatus());
        }

        @Test
        @DisplayName("Exact boundary 0.50 => automatic PRESENT")
        void testBoundary50AutomaticallyPresent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.50," +
                    "  \"secondSimilarity\": 0.20," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());
            assertTrue(record.getConfirmed());
            assertEquals(1, result.getStudentsMatched());
            assertEquals(0, result.getStudentsInReview());
            assertEquals("PRESENT", result.getDetectedFaces().get(0).getStatus());
        }

        @Test
        @DisplayName("Exact boundary 0.5001 => automatic PRESENT")
        void testBoundary5001AutomaticallyPresent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.5001," +
                    "  \"secondSimilarity\": 0.20," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());
            assertTrue(record.getConfirmed());
            assertEquals(1, result.getStudentsMatched());
            assertEquals("PRESENT", result.getDetectedFaces().get(0).getStatus());
        }

        @Test
        @DisplayName("Similarity >= 0.50 with a small margin (e.g. 0.02) qualifies as automatic PRESENT")
        void testSimilarityAbove50PercentWithSmallMarginMarksPresent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // sim 0.52, secondSim 0.50 -> margin 0.02 (< old 0.05 margin)
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.52," +
                    "  \"secondSimilarity\": 0.50," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());
            assertTrue(record.getConfirmed());
            assertEquals("PRESENT", result.getDetectedFaces().get(0).getStatus());
        }

        @Test
        @DisplayName("Identity safety: Ambiguous tie with identical top similarity routes to REVIEW")
        void testAmbiguousTieAcrossCandidatesRoutesToReview() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // sim 0.52, secondSim 0.52 -> identical score tie
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.52," +
                    "  \"secondSimilarity\": 0.52," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.REVIEW, record.getFinalStatus());
            assertTrue(record.getStatusReason().contains("Ambiguous biometric match"));
            assertEquals("REVIEW", result.getDetectedFaces().get(0).getStatus());
        }

        @Test
        @DisplayName("Missing or null similarity safely handled as UNMATCHED and never accidental PRESENT")
        void testMissingOrNullSimilaritySafelyHandledAsUnmatched() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 0," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.90," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": null," +
                    "  \"secondSimilarity\": null," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.PENDING, record.getFinalStatus());
            assertFalse(Boolean.TRUE.equals(record.getConfirmed()));
            assertEquals(1, result.getUnmatchedFaces());
            assertEquals("UNMATCHED", result.getDetectedFaces().get(0).getStatus());
            assertNull(result.getDetectedFaces().get(0).getMatchedStudentId());
        }

        @Test
        @DisplayName("Candidate not enrolled in session division is rejected as UNMATCHED")
        void testIneligibleOrUnenrolledCandidateNotMarkedPresent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            User otherDivUser = new User("External Student", "ext@intellicampus.edu", "pass123", Role.STUDENT, "EXT-001");
            ReflectionTestUtils.setField(otherDivUser, "id", 999L);
            StudentProfile otherDivStudent = new StudentProfile(otherDivUser, "2024-EXT-099");
            Division otherDiv = new Division("TE-CS-B", 2024);
            ReflectionTestUtils.setField(otherDiv, "id", 99L);
            otherDivStudent.setDivision(otherDiv);
            ReflectionTestUtils.setField(otherDivStudent, "id", 999L);

            when(studentProfileRepository.findById(999L)).thenReturn(Optional.of(otherDivStudent));
            lenient().when(recordRepository.findBySessionIdAndStudentId(100L, 999L)).thenReturn(Optional.empty());

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 999," +
                    "  \"similarity\": 0.88," +
                    "  \"secondSimilarity\": 0.10," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(0, result.getStudentsMatched());
            assertEquals(1, result.getUnmatchedFaces());
            assertEquals("UNMATCHED", result.getDetectedFaces().get(0).getStatus());
            assertNull(result.getDetectedFaces().get(0).getMatchedStudentId());
        }

        @Test
        @DisplayName("Low confidence face (< 0.20) does not mark nearest candidate ABSENT; keeps record PENDING")
        void testLowConfidenceFaceDoesNotMarkCandidateAbsent() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.85," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.15," +
                    "  \"secondSimilarity\": 0.08," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.PENDING, record.getFinalStatus());
            verify(recordRepository, never()).save(record);
        }

        @Test
        @DisplayName("Faculty manual override is preserved against subsequent photo AI detections")
        void testFacultyManualOverridePreservedAgainstSubsequentPhoto() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(1L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            AttendanceRecord overriddenRecord = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.ABSENT);
            overriddenRecord.setStatusReason("Faculty manual override: Student excused");
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(overriddenRecord));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(overriddenRecord));

            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.80," +
                    "  \"secondSimilarity\": 0.15," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertEquals(AttendanceStatus.ABSENT, overriddenRecord.getFinalStatus());
            assertTrue(overriddenRecord.getStatusReason().startsWith("Faculty manual override"));
        }

        @Test
        @DisplayName("Geofence rejection when coordinates are missing or out of bounds")
        void testGeofenceMissingOrInvalidCoordinatesRejection() {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});
            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            // Missing latitude
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, null, 73.85674, 10.0));
            assertTrue(ex1.getMessage().contains("mandatory"));

            // Geofence validator rejection
            doThrow(new GeofenceValidationException("Location is 250m away, exceeding classroom boundary of 50m."))
                    .when(geofenceValidator).validate(anyDouble(), anyDouble(), any(), anyDouble(), anyDouble(), any());

            assertThrows(GeofenceValidationException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.6000, 73.9000, 10.0));
        }

        @Test
        @DisplayName("Identity safeguard: Similarity < 0.20 is strictly UNMATCHED with null student ID and no roster modification")
        void testLowSimilarityBelow20PercentTreatedAsUnmatchedWithoutIdentity() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // Face with 0.18 similarity to student 10
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.88," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.18," +
                    "  \"secondSimilarity\": 0.05," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(1, result.getFacesDetected());
            assertEquals(0, result.getStudentsMatched());
            assertEquals(0, result.getStudentsInReview());
            assertEquals(1, result.getUnmatchedFaces());

            DetectedFaceDto faceDto = result.getDetectedFaces().get(0);
            assertEquals("UNMATCHED", faceDto.getStatus());
            assertNull(faceDto.getMatchedStudentId());
            assertNull(faceDto.getStudentName());
            assertEquals(AttendanceStatus.PENDING, record.getFinalStatus());
            verify(recordRepository, never()).save(record);
        }

        @Test
        @DisplayName("Identity safeguard: Duplicate assignment of same candidate in single photo enforces strict 1-to-1")
        void testDuplicateCandidateAssignmentInSamePhotoEnforcesStrictOneToOne() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // AI service returned student 10 for BOTH faces
            String aiResponseJson = "{" +
                    "\"faceCount\": 2," +
                    "\"matchedCount\": 2," +
                    "\"faces\": [" +
                    "  {\"faceId\": 1, \"quality\": 0.95, \"matchedStudentId\": 10, \"similarity\": 0.85, \"secondSimilarity\": 0.15, \"spoofScore\": null}," +
                    "  {\"faceId\": 2, \"quality\": 0.92, \"matchedStudentId\": 10, \"similarity\": 0.70, \"secondSimilarity\": 0.12, \"spoofScore\": null}" +
                    "]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(2, result.getFacesDetected());
            assertEquals(1, result.getStudentsMatched());
            assertEquals(1, result.getUnmatchedFaces());

            // Face 1 was assigned to student 10
            DetectedFaceDto f1 = result.getDetectedFaces().get(0);
            assertEquals("PRESENT", f1.getStatus());
            assertEquals(10L, f1.getMatchedStudentId());

            // Face 2 was prevented from duplicating student 10 -> marked UNMATCHED
            DetectedFaceDto f2 = result.getDetectedFaces().get(1);
            assertEquals("UNMATCHED", f2.getStatus());
            assertNull(f2.getMatchedStudentId());
            assertNull(f2.getStudentName());
        }

        @Test
        @DisplayName("Identity safeguard: Student already PRESENT never gets falsely assigned to low-confidence face in subsequent photo")
        void testAlreadyPresentStudentNeverFalselyAssignedToLowConfidenceFaceInSubsequentPhoto() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class_photo2.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(1L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // In photo 2, an unrelated person's face had 0.24 similarity to student 10
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.85," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.24," +
                    "  \"secondSimilarity\": 0.12," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            // Student 10 is ALREADY confirmed PRESENT (0.69) from photo 1
            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, 0.69, null, AttendanceStatus.PRESENT);
            record.setConfirmed(true);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(1, result.getFacesDetected());
            assertEquals(0, result.getStudentsMatched());
            assertEquals(0, result.getStudentsInReview());
            assertEquals(1, result.getUnmatchedFaces());

            // The unrelated face in photo 2 must NOT be labeled as Student 10!
            DetectedFaceDto faceDto = result.getDetectedFaces().get(0);
            assertEquals("UNMATCHED", faceDto.getStatus());
            assertNull(faceDto.getMatchedStudentId());
            assertNull(faceDto.getStudentName());

            // Student 10's attendance record is preserved as PRESENT (0.69)
            assertEquals(AttendanceStatus.PRESENT, record.getFinalStatus());
            assertEquals(0.69, record.getFaceScore());
        }

        @Test
        @DisplayName("Identity safeguard: Ambiguous tie within margin routes to REVIEW")
        void testAmbiguousTieMarginDemotedToReview() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // sim 0.51, secondSim 0.50 -> margin difference 0.01 (< effectiveMargin 0.02)
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.51," +
                    "  \"secondSimilarity\": 0.50," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.REVIEW, record.getFinalStatus());
            assertEquals("REVIEW", result.getDetectedFaces().get(0).getStatus());
            assertTrue(record.getStatusReason().contains("Ambiguous biometric match"));
        }

        @Test
        @DisplayName("Identity safeguard: Residual candidate conflict routes to REVIEW")
        void testResidualConflictDemotedToReview() throws Exception {
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionPhotoRepository.countBySession(openSession)).thenReturn(0L);
            when(sessionPhotoRepository.existsBySessionIdAndSha256(eq(100L), anyString())).thenReturn(false);
            when(sessionPhotoRepository.save(any(SessionPhoto.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));

            // secondSimilarity (0.75) > similarity (0.60): face actually resembles candidate 2 more than candidate 1
            String aiResponseJson = "{" +
                    "\"faceCount\": 1," +
                    "\"matchedCount\": 1," +
                    "\"faces\": [{" +
                    "  \"faceId\": 1," +
                    "  \"quality\": 0.95," +
                    "  \"matchedStudentId\": 10," +
                    "  \"similarity\": 0.60," +
                    "  \"secondSimilarity\": 0.75," +
                    "  \"spoofScore\": null" +
                    "}]}";

            when(restTemplate.postForEntity(contains("/ai/recognize"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(aiResponseJson, HttpStatus.OK));

            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record));

            PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0);

            assertNotNull(result);
            assertEquals(AttendanceStatus.REVIEW, record.getFinalStatus());
            assertEquals("REVIEW", result.getDetectedFaces().get(0).getStatus());
        }
    }

    @Nested
    @DisplayName("Session Start, Lifecycle & Finalisation Tests")
    class SessionLifecycleTests {

        @Test
        @DisplayName("Should successfully start a new session and initialize student roster as PENDING")
        void testStartSession() {
            StartSessionRequestDto req = new StartSessionRequestDto();
            req.setFacultyId(1L);
            req.setSubjectId(5L);
            req.setDivisionId(3L);
            req.setRoomId(7L);
            req.setChallengeCode("K7Q2");
            req.setPhotoWindowMinutes(10);

            when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
            when(subjectRepository.findById(5L)).thenReturn(Optional.of(subject));
            when(divisionRepository.findById(3L)).thenReturn(Optional.of(division));
            when(classroomRepository.findById(7L)).thenReturn(Optional.of(classroom));
            when(sessionRepository.findFirstByFacultyAndStatus(facultyUser, SessionStatus.OPEN)).thenReturn(Optional.empty());

            when(sessionRepository.save(any(AttendanceSession.class))).thenAnswer(invocation -> {
                AttendanceSession s = invocation.getArgument(0);
                ReflectionTestUtils.setField(s, "id", 101L);
                return s;
            });

            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));
            when(sessionRepository.findById(101L)).thenAnswer(inv -> {
                AttendanceSession s = new AttendanceSession(facultyUser, subject, division, classroom, "K7Q2", 10);
                ReflectionTestUtils.setField(s, "id", 101L);
                return Optional.of(s);
            });
            when(recordRepository.findBySession(any(AttendanceSession.class))).thenReturn(new ArrayList<>());

            SessionResponseDto result = attendanceService.startSession(req);

            assertNotNull(result);
            assertEquals("K7Q2", result.getChallengeCode());
            assertEquals("Dr. Ramesh Joshi", result.getFacultyName());
            assertEquals("Distributed Systems", result.getSubjectName());

            verify(sessionRepository).save(any(AttendanceSession.class));
            verify(recordRepository).save(any(AttendanceRecord.class));
        }

        @Test
        @DisplayName("Finalising session marks unresolved PENDING records as ABSENT")
        void testFinaliseSessionMarksPendingAsAbsent() {
            AttendanceRecord pendingRecord = new AttendanceRecord(openSession, studentProfile, null, null, AttendanceStatus.PENDING);

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(pendingRecord));
            when(sessionRepository.save(openSession)).thenReturn(openSession);

            SessionResponseDto result = attendanceService.transitionSessionStatus(100L, SessionStatus.FINALISED);

            assertNotNull(result);
            assertEquals(SessionStatus.FINALISED, openSession.getStatus());
            assertEquals(AttendanceStatus.ABSENT, pendingRecord.getFinalStatus());
            verify(recordRepository).save(pendingRecord);
        }

        @Test
        @DisplayName("Finalising session preserves confirmed PRESENT and faculty REVIEW records, converting only unresolved PENDING to ABSENT")
        void testFinaliseSessionPreservesPresentAndReviewWhileMarkingPendingAbsent() {
            // Student 1: Confirmed PRESENT
            AttendanceRecord presentRecord = new AttendanceRecord(openSession, studentProfile, 0.75, null, AttendanceStatus.PRESENT);
            presentRecord.setConfirmed(true);

            // Student 2: REVIEW (borderline match awaiting faculty decision)
            User user2 = new User("Priya Sharma", "priya@intellicampus.edu", "pass123", Role.STUDENT, "STU-102");
            ReflectionTestUtils.setField(user2, "id", 22L);
            StudentProfile profile2 = new StudentProfile(user2, "2024-CS-043");
            ReflectionTestUtils.setField(profile2, "id", 20L);
            AttendanceRecord reviewRecord = new AttendanceRecord(openSession, profile2, 0.35, null, AttendanceStatus.REVIEW);
            reviewRecord.setConfirmed(false);

            // Student 3: PENDING (no qualifying face match / low confidence detection < 0.20)
            User user3 = new User("Rohan Verma", "rohan@intellicampus.edu", "pass123", Role.STUDENT, "STU-103");
            ReflectionTestUtils.setField(user3, "id", 33L);
            StudentProfile profile3 = new StudentProfile(user3, "2024-CS-044");
            ReflectionTestUtils.setField(profile3, "id", 30L);
            AttendanceRecord pendingRecord = new AttendanceRecord(openSession, profile3, null, null, AttendanceStatus.PENDING);

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(presentRecord, reviewRecord, pendingRecord));
            when(sessionRepository.save(openSession)).thenReturn(openSession);

            SessionResponseDto result = attendanceService.transitionSessionStatus(100L, SessionStatus.FINALISED);

            assertNotNull(result);
            assertEquals(SessionStatus.FINALISED, openSession.getStatus());
            // Confirmed PRESENT is preserved
            assertEquals(AttendanceStatus.PRESENT, presentRecord.getFinalStatus());
            assertTrue(presentRecord.getConfirmed());
            // REVIEW is preserved so faculty can still inspect/verify
            assertEquals(AttendanceStatus.REVIEW, reviewRecord.getFinalStatus());
            // Unresolved PENDING transitions to ABSENT
            assertEquals(AttendanceStatus.ABSENT, pendingRecord.getFinalStatus());
            assertTrue(pendingRecord.getStatusReason().contains("Student did not verify attendance before session finalisation"));

            verify(recordRepository, times(1)).save(pendingRecord);
            verify(recordRepository, never()).save(presentRecord);
            verify(recordRepository, never()).save(reviewRecord);
        }

        @Test
        @DisplayName("Reject photo upload for already finalised session")
        void testFinalisedSessionCannotAcceptPhotos() {
            openSession.setStatus(SessionStatus.FINALISED);
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0));
            assertTrue(ex.getMessage().contains("finalised"));
        }

        @Test
        @DisplayName("Reject reopening a finalised or expired session")
        void testCannotReopenFinalisedSession() {
            openSession.setStatus(SessionStatus.FINALISED);
            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            assertThrows(IllegalStateException.class, () ->
                    attendanceService.transitionSessionStatus(100L, SessionStatus.OPEN));
        }

        @Test
        @DisplayName("Unauthorized faculty cannot upload photo, transition status, or override record")
        void testUnauthorizedFacultyRejection() {
            User otherFaculty = new User("Dr. Sunita Rao", "sunita@intellicampus.edu", "pass123", Role.FACULTY, "FAC-002");
            ReflectionTestUtils.setField(otherFaculty, "id", 99L);

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            // Unauthorized photo upload
            assertThrows(AccessDeniedException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0, null, otherFaculty));

            // Unauthorized status transition
            assertThrows(AccessDeniedException.class, () ->
                    attendanceService.transitionSessionStatus(100L, SessionStatus.EXPIRED, otherFaculty));

            // Unauthorized manual override
            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, 0.40, null, AttendanceStatus.REVIEW);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));

            ManualOverrideDto override = new ManualOverrideDto();
            override.setStatus(AttendanceStatus.PRESENT);
            assertThrows(AccessDeniedException.class, () ->
                    attendanceService.overrideRecordStatus(100L, 10L, override, otherFaculty));
        }

        @Test
        @DisplayName("Admin or HOD can manage another faculty's session")
        void testAdminOrHodCanManageSession() {
            User adminUser = new User("System Admin", "admin@intellicampus.edu", "pass123", Role.PRINCIPAL, "ADM-001");
            ReflectionTestUtils.setField(adminUser, "id", 999L);

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionRepository.save(openSession)).thenReturn(openSession);
            when(recordRepository.findBySession(openSession)).thenReturn(new ArrayList<>());

            assertDoesNotThrow(() ->
                    attendanceService.transitionSessionStatus(100L, SessionStatus.EXPIRED, adminUser));
        }

        @Test
        @DisplayName("Faculty manual override sets status and updates reason")
        void testManualOverride() {
            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, 0.40, null, AttendanceStatus.REVIEW);

            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            ManualOverrideDto override = new ManualOverrideDto();
            override.setStatus(AttendanceStatus.PRESENT);
            override.setReason("Student visually confirmed in bench row 2");

            AttendanceRecordResponseDto res = attendanceService.overrideRecordStatus(100L, 10L, override);

            assertNotNull(res);
            assertEquals(AttendanceStatus.PRESENT, res.getFinalStatus());
            assertTrue(res.getConfirmed());
            assertTrue(res.getStatusReason().contains("Faculty manual override: Student visually confirmed"));
        }

        @Test
        @DisplayName("Security: Possession of X-Session-Token cannot bypass session expiry")
        void testSessionTokenCannotBypassExpiredSession() {
            openSession.setSessionNonce("valid-nonce-abc-123");
            openSession.setPhotoWindowEndsAt(LocalDateTime.now().minusMinutes(10));
            openSession.setExpiresAt(LocalDateTime.now().minusMinutes(10));
            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0, "valid-nonce-abc-123", facultyUser));
            assertTrue(ex.getMessage().contains("expired"));
        }

        @Test
        @DisplayName("Security: Possession of X-Session-Token cannot bypass faculty authorization")
        void testSessionTokenCannotBypassUnauthorizedFaculty() {
            openSession.setSessionNonce("valid-nonce-abc-123");
            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            User unauthorizedFaculty = new User("Dr. Imposter", "imposter@intellicampus.edu", "pass123", Role.FACULTY, "FAC-999");
            ReflectionTestUtils.setField(unauthorizedFaculty, "id", 999L);

            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0, "valid-nonce-abc-123", unauthorizedFaculty));
            assertTrue(ex.getMessage().contains("not authorized"));
        }

        @Test
        @DisplayName("Security: Cannot override records for an already finalised session")
        void testCannotOverrideRecordOnFinalisedSession() {
            openSession.setStatus(SessionStatus.FINALISED);
            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, 0.40, null, AttendanceStatus.REVIEW);
            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));

            ManualOverrideDto override = new ManualOverrideDto();
            override.setStatus(AttendanceStatus.PRESENT);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    attendanceService.overrideRecordStatus(100L, 10L, override, facultyUser));
            assertTrue(ex.getMessage().contains("already finalised"));
        }

        @Test
        @DisplayName("Security: Authenticated faculty identity controls session creation; client cannot impersonate another facultyId")
        void testFacultyCannotImpersonateAnotherFacultyOnStartSession() {
            StartSessionRequestDto req = new StartSessionRequestDto();
            req.setFacultyId(999L); // Client attempts to supply another faculty ID
            req.setSubjectId(5L);
            req.setDivisionId(3L);
            req.setRoomId(7L);
            req.setChallengeCode("K7Q2");

            when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser)); // Resolved to caller's id (1L), not 999L
            when(subjectRepository.findById(5L)).thenReturn(Optional.of(subject));
            when(divisionRepository.findById(3L)).thenReturn(Optional.of(division));
            when(classroomRepository.findById(7L)).thenReturn(Optional.of(classroom));
            when(sessionRepository.findFirstByFacultyAndStatus(facultyUser, SessionStatus.OPEN)).thenReturn(Optional.empty());

            when(sessionRepository.save(any(AttendanceSession.class))).thenAnswer(invocation -> {
                AttendanceSession s = invocation.getArgument(0);
                assertEquals(1L, s.getFaculty().getId(), "Faculty ID must match authenticated caller, not client-supplied ID");
                ReflectionTestUtils.setField(s, "id", 102L);
                return s;
            });

            when(studentProfileRepository.findByDivisionId(3L)).thenReturn(List.of(studentProfile));
            when(sessionRepository.findById(102L)).thenAnswer(inv -> {
                AttendanceSession s = new AttendanceSession(facultyUser, subject, division, classroom, "K7Q2", 10);
                ReflectionTestUtils.setField(s, "id", 102L);
                return Optional.of(s);
            });
            when(recordRepository.findBySession(any(AttendanceSession.class))).thenReturn(new ArrayList<>());

            SessionResponseDto result = attendanceService.startSession(req, facultyUser);

            assertNotNull(result);
            verify(userRepository).findById(1L); // Caller's ID was used!
            verify(userRepository, never()).findById(999L); // Impersonated ID was rejected!
        }
    }

    @Nested
    @DisplayName("Student Attendance History & Authorization Tests")
    class StudentAttendanceHistoryTests {

        private User studentUser2;
        private StudentProfile studentProfile2;
        private AttendanceRecord recordStudent1;
        private AttendanceRecord recordStudent2;

        @BeforeEach
        void setUpStudentHistory() {
            studentUser2 = new User("Priya Sharma", "priya@intellicampus.edu", "pass123", Role.STUDENT, "STU-102");
            ReflectionTestUtils.setField(studentUser2, "id", 20L);

            studentProfile2 = new StudentProfile(studentUser2, "2024-CS-043");
            ReflectionTestUtils.setField(studentProfile2, "id", 200L);

            openSession.setStatus(SessionStatus.FINALISED);

            recordStudent1 = new AttendanceRecord(openSession, studentProfile, 0.85, null, AttendanceStatus.PRESENT);
            recordStudent1.setConfirmed(true);
            recordStudent1.setStatusReason("Biometric face recognition verified");
            ReflectionTestUtils.setField(recordStudent1, "id", 501L);

            recordStudent2 = new AttendanceRecord(openSession, studentProfile2, null, null, AttendanceStatus.ABSENT);
            recordStudent2.setConfirmed(false);
            recordStudent2.setStatusReason("Student did not verify attendance before session finalisation");
            ReflectionTestUtils.setField(recordStudent2, "id", 502L);
        }

        @Test
        @DisplayName("Criterion 1 & 4: Authenticated student can view own attendance by Profile ID or User ID")
        void testStudentCanViewOwnAttendanceByProfileOrUserId() {
            when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(studentProfile));
            when(recordRepository.findByStudentOrderByCreatedAtDesc(studentProfile)).thenReturn(List.of(recordStudent1));

            // Query using Profile ID (10L)
            List<AttendanceRecordResponseDto> byProfileId = attendanceService.getRecordsForStudent(10L, studentUser);
            assertNotNull(byProfileId);
            assertEquals(1, byProfileId.size());
            assertEquals(10L, byProfileId.get(0).getStudentId());
            assertEquals(AttendanceStatus.PRESENT, byProfileId.get(0).getFinalStatus());
            assertEquals("CS301", byProfileId.get(0).getSubjectCode());
            assertEquals("Distributed Systems", byProfileId.get(0).getSubjectName());
            assertEquals(100L, byProfileId.get(0).getSessionId());
            assertEquals(SessionStatus.FINALISED, byProfileId.get(0).getSessionStatus());

            // Query using User ID (2L)
            List<AttendanceRecordResponseDto> byUserId = attendanceService.getRecordsForStudent(2L, studentUser);
            assertNotNull(byUserId);
            assertEquals(1, byUserId.size());
            assertEquals(10L, byUserId.get(0).getStudentId());
        }

        @Test
        @DisplayName("Criterion 2: Student CANNOT access another student's attendance history (403 AccessDeniedException)")
        void testStudentCannotAccessAnotherStudentAttendance() {
            when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(studentProfile));

            // Student 1 (User ID 2, Profile ID 10) attempts to query Student 2's ID (200L or 20L)
            AccessDeniedException ex1 = assertThrows(AccessDeniedException.class, () ->
                    attendanceService.getRecordsForStudent(200L, studentUser));
            assertTrue(ex1.getMessage().contains("only permitted to view their own"));

            AccessDeniedException ex2 = assertThrows(AccessDeniedException.class, () ->
                    attendanceService.getRecordsForStudent(20L, studentUser));
            assertTrue(ex2.getMessage().contains("only permitted to view their own"));

            verify(recordRepository, never()).findByStudentOrderByCreatedAtDesc(studentProfile2);
        }

        @Test
        @DisplayName("Criterion 1: Two different students receive their own distinct attendance records")
        void testTwoDifferentStudentsReceiveOwnAttendanceRecords() {
            when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(studentProfile));
            when(studentProfileRepository.findByUserId(20L)).thenReturn(Optional.of(studentProfile2));

            when(recordRepository.findByStudentOrderByCreatedAtDesc(studentProfile)).thenReturn(List.of(recordStudent1));
            when(recordRepository.findByStudentOrderByCreatedAtDesc(studentProfile2)).thenReturn(List.of(recordStudent2));

            // Student 1 query
            List<AttendanceRecordResponseDto> student1Records = attendanceService.getRecordsForStudent(10L, studentUser);
            assertEquals(1, student1Records.size());
            assertEquals(AttendanceStatus.PRESENT, student1Records.get(0).getFinalStatus());
            assertEquals(0.85, student1Records.get(0).getFaceScore());
            assertEquals("Aarav Mehta", student1Records.get(0).getStudentName());

            // Student 2 query
            List<AttendanceRecordResponseDto> student2Records = attendanceService.getRecordsForStudent(200L, studentUser2);
            assertEquals(1, student2Records.size());
            assertEquals(AttendanceStatus.ABSENT, student2Records.get(0).getFinalStatus());
            assertNull(student2Records.get(0).getFaceScore());
            assertEquals("Priya Sharma", student2Records.get(0).getStudentName());
        }

        @Test
        @DisplayName("Criterion 3 & 5: Faculty and Admin can view attendance records for any student")
        void testFacultyAndAdminCanViewStudentAttendance() {
            User adminUser = new User("Admin", "admin@intellicampus.edu", "pass123", Role.PRINCIPAL, "ADM-001");
            ReflectionTestUtils.setField(adminUser, "id", 999L);

            when(studentProfileRepository.findById(10L)).thenReturn(Optional.of(studentProfile));
            when(recordRepository.findByStudentOrderByCreatedAtDesc(studentProfile)).thenReturn(List.of(recordStudent1));

            // Faculty query
            List<AttendanceRecordResponseDto> facultyResult = attendanceService.getRecordsForStudent(10L, facultyUser);
            assertEquals(1, facultyResult.size());
            assertEquals("Aarav Mehta", facultyResult.get(0).getStudentName());

            // Admin query
            List<AttendanceRecordResponseDto> adminResult = attendanceService.getRecordsForStudent(10L, adminUser);
            assertEquals(1, adminResult.size());
            assertEquals("Aarav Mehta", adminResult.get(0).getStudentName());
        }
    }
}