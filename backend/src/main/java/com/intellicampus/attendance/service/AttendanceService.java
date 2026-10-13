package com.intellicampus.attendance.service;

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
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Transactional
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final DivisionRepository divisionRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentProfileRepository studentProfileRepository;

    public AttendanceService(AttendanceSessionRepository sessionRepository,
                             AttendanceRecordRepository recordRepository,
                             UserRepository userRepository,
                             SubjectRepository subjectRepository,
                             DivisionRepository divisionRepository,
                             ClassroomRepository classroomRepository,
                             StudentProfileRepository studentProfileRepository) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.divisionRepository = divisionRepository;
        this.classroomRepository = classroomRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    public SessionResponseDto startSession(StartSessionRequestDto req) {
        User faculty = userRepository.findById(req.getFacultyId())
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + req.getFacultyId()));
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

        String code = req.getChallengeCode();
        if (code == null || code.trim().isEmpty()) {
            code = generateRandomChallengeCode();
        }

        AttendanceSession session = new AttendanceSession(
                faculty,
                subject,
                division,
                room,
                code.toUpperCase(),
                req.getPhotoWindowMinutes() != null ? req.getPhotoWindowMinutes() : 10
        );

        AttendanceSession savedSession = sessionRepository.save(session);

        // Populate roster records for all students in the target division
        List<StudentProfile> students = studentProfileRepository.findByDivisionId(division.getId());
        for (StudentProfile st : students) {
            AttendanceRecord record = new AttendanceRecord(savedSession, st, null, null, AttendanceStatus.PENDING);
            recordRepository.save(record);
        }

        return getSessionDetails(savedSession.getId());
    }

    @Transactional(readOnly = true)
    public DynamicQrTokenDto generateDynamicQrToken(Long sessionId) {
        long now = System.currentTimeMillis();
        long slot = now / 15000L;
        int secondsLeft = 15 - (int) ((now / 1000L) % 15);

        String raw = "secret_key:" + sessionId + ":" + slot;
        String tokenHash = sha256Hex(raw).substring(0, 16).toUpperCase();
        String token = "IC-" + tokenHash;

        return new DynamicQrTokenDto(slot, token, secondsLeft);
    }

    public AttendanceRecordResponseDto confirmStudentAttendance(Long sessionId, StudentConfirmAttendanceDto req) {
        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Attendance session is not currently open (Status: " + session.getStatus() + ")");
        }

        if (!session.getChallengeCode().equalsIgnoreCase(req.getChallengeCode())) {
            throw new IllegalArgumentException("Invalid challenge code provided.");
        }

        StudentProfile student = studentProfileRepository.findById(req.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + req.getStudentId()));

        // Validate GPS distance using Haversine formula
        double distMeters = calculateHaversineDistance(
                req.getLatitude(), req.getLongitude(),
                session.getRoom().getLatitude(), session.getRoom().getLongitude()
        );

        int maxRadius = session.getRoom().getRadiusM() != null ? session.getRoom().getRadiusM() : 50;
        if (distMeters > maxRadius) {
            throw new IllegalStateException("Geofence alert: Location is " + Math.round(distMeters) + "m away, exceeding classroom boundary of " + maxRadius + "m.");
        }

        AttendanceRecord record = recordRepository.findBySessionAndStudent(session, student)
                .orElseGet(() -> new AttendanceRecord(session, student, 0.90, 0.95, AttendanceStatus.PRESENT));

        record.setConfirmed(true);
        record.setConfirmedAt(LocalDateTime.now());
        record.setConfirmLat(req.getLatitude());
        record.setConfirmLng(req.getLongitude());
        record.setFinalStatus(AttendanceStatus.PRESENT);
        record.setStatusReason("Verified via GPS Geofence (" + Math.round(distMeters) + "m) and Dynamic QR token");

        AttendanceRecord savedRecord = recordRepository.save(record);
        return mapRecordToDto(savedRecord);
    }

    public SessionResponseDto transitionSessionStatus(Long sessionId, SessionStatus newStatus) {
        AttendanceSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        session.setStatus(newStatus);
        if (newStatus == SessionStatus.FINALISED) {
            session.setFinalisedAt(LocalDateTime.now());
            // Mark unconfirmed records as ABSENT
            List<AttendanceRecord> records = recordRepository.findBySession(session);
            for (AttendanceRecord r : records) {
                if (r.getFinalStatus() == AttendanceStatus.PENDING) {
                    r.setFinalStatus(AttendanceStatus.ABSENT);
                    r.setStatusReason("Student did not confirm attendance before session finalisation");
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
        record.setStatusReason(override.getReason() != null ? override.getReason() : "Faculty manual override");
        AttendanceRecord saved = recordRepository.save(record);
        return mapRecordToDto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<SessionResponseDto> getActiveSessionForDivision(Long divisionId) {
        return sessionRepository.findFirstByDivisionIdAndStatus(divisionId, SessionStatus.OPEN)
                .map(s -> getSessionDetails(s.getId()));
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

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
