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
        attendanceService.setFaceMatchThreshold(0.45);
        attendanceService.setFaceMatchMargin(0.05);

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
    }

    @Nested
    @DisplayName("Face Recognition Attendance & Matching Tests")
    class FaceRecognitionTests {

        @Test
        @DisplayName("Criterion 1: Cosine similarity >= 0.45 and margin >= 0.05 marks student PRESENT")
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
            assertEquals(0.72, record.getFaceScore());
            assertNull(record.getSpoofScore());
            assertTrue(record.getStatusReason().contains("Biometric face recognition verified"));
        }

        @Test
        @DisplayName("Criterion 2: Borderline similarity in [0.35, 0.45) flags student for REVIEW")
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
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));
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
        @DisplayName("Criterion 5: Reject photo upload when photo window expired")
        void testPhotoWindowExpired() {
            openSession.setPhotoWindowEndsAt(LocalDateTime.now().minusMinutes(5));
            MockMultipartFile photo = new MockMultipartFile("photo", "class.jpg", "image/jpeg", new byte[]{1, 2});

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    attendanceService.processClassroomPhoto(100L, photo, 18.52043, 73.85674, 10.0));
            assertTrue(ex.getMessage().contains("expired"));
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
    }
}