package com.intellicampus.attendance.controller;

import com.intellicampus.attendance.dto.*;
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.attendance.service.AttendanceService;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/attendance/sessions")
@CrossOrigin(origins = "*")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    @PostMapping
    public ResponseEntity<SessionResponseDto> startSession(
            @RequestBody StartSessionRequestDto req,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        SessionResponseDto session = currentUser != null
                ? attendanceService.startSession(req, currentUser)
                : attendanceService.startSession(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponseDto> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(attendanceService.getSessionDetails(id));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoRecognitionResultDto> uploadClassroomPhoto(
            @PathVariable Long id,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "accuracyM", required = false) Double accuracyM,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        PhotoRecognitionResultDto result = (sessionToken != null || currentUser != null)
                ? attendanceService.processClassroomPhoto(id, photo, latitude, longitude, accuracyM, sessionToken, currentUser)
                : attendanceService.processClassroomPhoto(id, photo, latitude, longitude, accuracyM);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<SessionResponseDto> updateSessionStatus(
            @PathVariable Long id,
            @RequestParam SessionStatus status,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        SessionResponseDto session = currentUser != null
                ? attendanceService.transitionSessionStatus(id, status, currentUser)
                : attendanceService.transitionSessionStatus(id, status);
        return ResponseEntity.ok(session);
    }

    @PutMapping("/{id}/records/{studentId}/override")
    public ResponseEntity<AttendanceRecordResponseDto> overrideRecord(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @RequestBody ManualOverrideDto override,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        AttendanceRecordResponseDto result = currentUser != null
                ? attendanceService.overrideRecordStatus(id, studentId, override, currentUser)
                : attendanceService.overrideRecordStatus(id, studentId, override);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/active")
    public ResponseEntity<SessionResponseDto> getActiveSession(@RequestParam Long divisionId) {
        return attendanceService.getActiveSessionForDivision(divisionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/records/student/{studentId}")
    public ResponseEntity<List<AttendanceRecordResponseDto>> getStudentAttendanceHistory(
            @PathVariable Long studentId,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        if (currentUser != null && currentUser.getRole() == Role.STUDENT) {
            if (!currentUser.getId().equals(studentId)) {
                throw new AccessDeniedException("Students are only permitted to view their own attendance records.");
            }
        }
        return ResponseEntity.ok(attendanceService.getRecordsForStudent(studentId));
    }
}