package com.intellicampus.attendance.controller;

import com.intellicampus.attendance.dto.*;
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.attendance.service.AttendanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @PostMapping
    public ResponseEntity<SessionResponseDto> startSession(@RequestBody StartSessionRequestDto req) {
        SessionResponseDto session = attendanceService.startSession(req);
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
            @RequestParam(value = "accuracyM", required = false) Double accuracyM) {
        PhotoRecognitionResultDto result = attendanceService.processClassroomPhoto(id, photo, latitude, longitude, accuracyM);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<SessionResponseDto> updateSessionStatus(
            @PathVariable Long id,
            @RequestParam SessionStatus status) {
        return ResponseEntity.ok(attendanceService.transitionSessionStatus(id, status));
    }

    @PutMapping("/{id}/records/{studentId}/override")
    public ResponseEntity<AttendanceRecordResponseDto> overrideRecord(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @RequestBody ManualOverrideDto override) {
        return ResponseEntity.ok(attendanceService.overrideRecordStatus(id, studentId, override));
    }

    @GetMapping("/active")
    public ResponseEntity<SessionResponseDto> getActiveSession(@RequestParam Long divisionId) {
        return attendanceService.getActiveSessionForDivision(divisionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/records/student/{studentId}")
    public ResponseEntity<List<AttendanceRecordResponseDto>> getStudentAttendanceHistory(@PathVariable Long studentId) {
        return ResponseEntity.ok(attendanceService.getRecordsForStudent(studentId));
    }
}