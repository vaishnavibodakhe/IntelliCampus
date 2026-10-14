package com.intellicampus.attendance;

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
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.attendance.repository.AttendanceRecordRepository;
import com.intellicampus.attendance.repository.AttendanceSessionRepository;
import com.intellicampus.attendance.service.AttendanceService;
import com.intellicampus.attendance.validation.GeofenceValidationException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for AttendanceService covering dynamic QR token rotation,
 * session lifecycle, geofence check-in verification, and manual overrides.
 * Compliant with IC-SRS-1.0 Section 8.6 and Test Case TC-02.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceSessionRepository sessionRepository;
    @Mock
    private AttendanceRecordRepository recordRepository;
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

    @InjectMocks
    private AttendanceService attendanceService;

    private User facultyUser;
    private User studentUser;
    private StudentProfile studentProfile;
    private Subject subject;
    private Division division;
    private Classroom classroom;
    private AttendanceSession openSession;

    @BeforeEach
    void setUp() {
        facultyUser = new User("Dr. Ramesh Joshi", "ramesh@intellicampus.edu", "pass123", Role.FACULTY, "FAC-001");
        ReflectionTestUtils.setField(facultyUser, "id", 1L);

        studentUser = new User("Aarav Mehta", "aarav@intellicampus.edu", "pass123", Role.STUDENT, "STU-101");
        ReflectionTestUtils.setField(studentUser, "id", 2L);

        studentProfile = new StudentProfile(studentUser, "2024-CS-042");
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
    @DisplayName("Dynamic QR Token Rotation Tests (15-Second Slotting)")
    class DynamicQrTokenTests {

        @Test
        @DisplayName("Should generate rotating QR token with valid slot, prefix, and countdown")
        void testGenerateDynamicQrToken() {
            DynamicQrTokenDto tokenDto = attendanceService.generateDynamicQrToken(100L);

            assertNotNull(tokenDto);
            assertNotNull(tokenDto.getToken());
            assertTrue(tokenDto.getToken().startsWith("IC-"), "Dynamic QR token should have 'IC-' prefix");
            assertEquals(19, tokenDto.getToken().length(), "Token format should be IC- followed by 16 hex characters");

            assertTrue(tokenDto.getSecondsLeft() >= 1 && tokenDto.getSecondsLeft() <= 15,
                    "Countdown seconds should be between 1 and 15 seconds");

            long currentSlot = System.currentTimeMillis() / 15000L;
            assertEquals(currentSlot, tokenDto.getSlot(), "Slot index should match 15-second epoch window");
        }

        @Test
        @DisplayName("Should produce consistent token for the same session within identical time slot")
        void testTokenConsistencyWithinSlot() {
            DynamicQrTokenDto token1 = attendanceService.generateDynamicQrToken(100L);
            DynamicQrTokenDto token2 = attendanceService.generateDynamicQrToken(100L);

            if (token1.getSlot() == token2.getSlot()) {
                assertEquals(token1.getToken(), token2.getToken(), "Tokens in the same slot should be identical");
            }
        }
    }

    @Nested
    @DisplayName("Session Start and Roster Population Tests")
    class SessionStartTests {

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
        @DisplayName("Should expire previously open session for the faculty when a new session starts")
        void testExpirePreviousSessionOnNewStart() {
            AttendanceSession existingOpenSession = new AttendanceSession(facultyUser, subject, division, classroom, "OLD1", 10);
            ReflectionTestUtils.setField(existingOpenSession, "id", 99L);

            StartSessionRequestDto req = new StartSessionRequestDto();
            req.setFacultyId(1L);
            req.setSubjectId(5L);
            req.setDivisionId(3L);
            req.setRoomId(7L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(facultyUser));
            when(subjectRepository.findById(5L)).thenReturn(Optional.of(subject));
            when(divisionRepository.findById(3L)).thenReturn(Optional.of(division));
            when(classroomRepository.findById(7L)).thenReturn(Optional.of(classroom));
            when(sessionRepository.findFirstByFacultyAndStatus(facultyUser, SessionStatus.OPEN)).thenReturn(Optional.of(existingOpenSession));
            when(sessionRepository.save(any(AttendanceSession.class))).thenReturn(openSession);
            when(studentProfileRepository.findByDivisionId(anyLong())).thenReturn(List.of());
            when(sessionRepository.findById(anyLong())).thenReturn(Optional.of(openSession));
            when(recordRepository.findBySession(any())).thenReturn(List.of());

            attendanceService.startSession(req);

            assertEquals(SessionStatus.EXPIRED, existingOpenSession.getStatus());
            assertNotNull(existingOpenSession.getFinalisedAt());
            verify(sessionRepository, atLeastOnce()).save(existingOpenSession);
        }
    }

    @Nested
    @DisplayName("Attendance Confirmation & Geofence Verification Tests")
    class ConfirmationTests {

        @Test
        @DisplayName("Should successfully confirm student attendance within geofence perimeter")
        void testConfirmAttendanceSuccess() {
            StudentConfirmAttendanceDto req = new StudentConfirmAttendanceDto();
            req.setStudentId(10L);
            req.setChallengeCode("K7Q2");
            req.setLatitude(18.52044);
            req.setLongitude(73.85675);
            req.setAccuracyM(12.0);

            AttendanceRecord pendingRecord = new AttendanceRecord(openSession, studentProfile, 0.95, 0.92, AttendanceStatus.PENDING);
            ReflectionTestUtils.setField(pendingRecord, "id", 501L);

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(studentProfileRepository.findById(10L)).thenReturn(Optional.of(studentProfile));
            when(geofenceValidator.validate(eq(18.52044), eq(73.85675), eq(12.0), anyDouble(), anyDouble(), anyInt()))
                    .thenReturn(15.2);
            when(recordRepository.findBySessionAndStudent(openSession, studentProfile))
                    .thenReturn(Optional.of(pendingRecord));
            when(spoofScoreValidator.evaluateStatus(0.95, 0.92, true))
                    .thenReturn(AttendanceStatus.PRESENT);
            when(spoofScoreValidator.resolveStatusReason(0.95, 0.92, 15.2))
                    .thenReturn("Verified via GPS Geofence (15m) and Dynamic QR Token.");
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            AttendanceRecordResponseDto response = attendanceService.confirmStudentAttendance(100L, req);

            assertNotNull(response);
            assertTrue(response.getConfirmed());
            assertEquals(AttendanceStatus.PRESENT, response.getFinalStatus());
            assertEquals("Aarav Mehta", response.getStudentName());
            assertEquals("2024-CS-042", response.getRollNo());

            verify(geofenceValidator).validate(18.52044, 73.85675, 12.0, 18.52043, 73.85674, 50);
            verify(recordRepository).save(pendingRecord);
        }

        @Test
        @DisplayName("Should reject confirmation if attendance session is already closed or finalised")
        void testRejectIfSessionNotOpen() {
            openSession.setStatus(SessionStatus.FINALISED);
            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            StudentConfirmAttendanceDto req = new StudentConfirmAttendanceDto();
            req.setStudentId(10L);
            req.setChallengeCode("K7Q2");

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    attendanceService.confirmStudentAttendance(100L, req)
            );
            assertTrue(ex.getMessage().contains("not currently open"));
        }

        @Test
        @DisplayName("Should reject confirmation if challenge code does not match")
        void testRejectInvalidChallengeCode() {
            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));

            StudentConfirmAttendanceDto req = new StudentConfirmAttendanceDto();
            req.setStudentId(10L);
            req.setChallengeCode("WRONG_CODE");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    attendanceService.confirmStudentAttendance(100L, req)
            );
            assertTrue(ex.getMessage().contains("Invalid challenge code"));
        }

        @Test
        @DisplayName("Should reject check-in when GeofenceValidator throws GeofenceValidationException")
        void testRejectOutsideGeofence() {
            StudentConfirmAttendanceDto req = new StudentConfirmAttendanceDto();
            req.setStudentId(10L);
            req.setChallengeCode("K7Q2");
            req.setLatitude(18.52500);
            req.setLongitude(73.85675);
            req.setAccuracyM(10.0);

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(studentProfileRepository.findById(10L)).thenReturn(Optional.of(studentProfile));
            when(geofenceValidator.validate(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                    .thenThrow(new GeofenceValidationException("Geofence alert: Location is 510m away, exceeding classroom boundary of 50m.", 510.0, 50));

            assertThrows(GeofenceValidationException.class, () ->
                    attendanceService.confirmStudentAttendance(100L, req)
            );
        }
    }

    @Nested
    @DisplayName("Session Finalization and Manual Overrides")
    class FinalizationAndOverrideTests {

        @Test
        @DisplayName("Should mark all unconfirmed PENDING records as ABSENT when session is FINALISED")
        void testFinalizeSessionTransitionsPendingToAbsent() {
            AttendanceRecord record1 = new AttendanceRecord(openSession, studentProfile, 0.95, 0.90, AttendanceStatus.PRESENT);
            record1.setConfirmed(true);

            User student2User = new User("Priya Sharma", "priya@intellicampus.edu", "pass", Role.STUDENT, "STU-102");
            StudentProfile student2 = new StudentProfile(student2User, "2024-CS-043");
            AttendanceRecord record2 = new AttendanceRecord(openSession, student2, null, null, AttendanceStatus.PENDING);

            when(sessionRepository.findById(100L)).thenReturn(Optional.of(openSession));
            when(sessionRepository.save(any(AttendanceSession.class))).thenReturn(openSession);
            when(recordRepository.findBySession(openSession)).thenReturn(List.of(record1, record2));

            SessionResponseDto result = attendanceService.transitionSessionStatus(100L, SessionStatus.FINALISED);

            assertEquals(SessionStatus.FINALISED, openSession.getStatus());
            assertNotNull(openSession.getFinalisedAt());
            assertEquals(AttendanceStatus.ABSENT, record2.getFinalStatus());
            assertTrue(record2.getStatusReason().contains("did not confirm attendance"));

            verify(recordRepository).save(record2);
            verify(recordRepository, never()).save(record1); // Already PRESENT
        }

        @Test
        @DisplayName("Should allow faculty manual override of an individual attendance record")
        void testManualOverrideRecordStatus() {
            AttendanceRecord record = new AttendanceRecord(openSession, studentProfile, 0.45, 0.80, AttendanceStatus.REVIEW);
            record.setConfirmed(true);

            ManualOverrideDto overrideDto = new ManualOverrideDto();
            overrideDto.setStatus(AttendanceStatus.PRESENT);
            overrideDto.setReason("Faculty verified student presence in second row");

            when(recordRepository.findBySessionIdAndStudentId(100L, 10L)).thenReturn(Optional.of(record));
            when(recordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            AttendanceRecordResponseDto response = attendanceService.overrideRecordStatus(100L, 10L, overrideDto);

            assertNotNull(response);
            assertEquals(AttendanceStatus.PRESENT, response.getFinalStatus());
            assertEquals("Faculty verified student presence in second row", response.getStatusReason());
            verify(recordRepository).save(record);
        }
    }
}
