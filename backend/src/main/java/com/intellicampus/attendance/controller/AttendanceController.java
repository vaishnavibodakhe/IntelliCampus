package com.intellicampus.attendance.controller;

import com.intellicampus.attendance.dto.*;
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.attendance.service.AttendanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{id}/token")
    public ResponseEntity<DynamicQrTokenDto> getDynamicQrToken(@PathVariable Long id) {
        return ResponseEntity.ok(attendanceService.generateDynamicQrToken(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<AttendanceRecordResponseDto> confirmAttendance(
            @PathVariable Long id,
            @RequestBody StudentConfirmAttendanceDto req) {
        AttendanceRecordResponseDto confirmed = attendanceService.confirmStudentAttendance(id, req);
        return ResponseEntity.ok(confirmed);
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
}
