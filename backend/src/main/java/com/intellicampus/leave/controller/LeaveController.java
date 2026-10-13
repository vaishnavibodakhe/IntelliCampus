package com.intellicampus.leave.controller;

import com.intellicampus.leave.dto.LeaveDecisionDto;
import com.intellicampus.leave.dto.LeaveResponseDto;
import com.intellicampus.leave.dto.SubmitLeaveRequestDto;
import com.intellicampus.leave.service.LeaveService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@CrossOrigin(origins = "*")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping
    public ResponseEntity<LeaveResponseDto> submitLeave(@RequestBody SubmitLeaveRequestDto req) {
        LeaveResponseDto created = leaveService.submitLeave(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/mentor")
    public ResponseEntity<LeaveResponseDto> processMentorReview(
            @PathVariable Long id,
            @RequestBody LeaveDecisionDto decision) {
        return ResponseEntity.ok(leaveService.processMentorReview(id, decision));
    }

    @PutMapping("/{id}/hod")
    public ResponseEntity<LeaveResponseDto> processHodReview(
            @PathVariable Long id,
            @RequestBody LeaveDecisionDto decision) {
        return ResponseEntity.ok(leaveService.processHodReview(id, decision));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<LeaveResponseDto>> getStudentLeaves(@PathVariable Long studentId) {
        return ResponseEntity.ok(leaveService.getStudentLeaves(studentId));
    }

    @GetMapping("/mentor/{mentorId}/pending")
    public ResponseEntity<List<LeaveResponseDto>> getPendingMentorLeaves(@PathVariable Long mentorId) {
        return ResponseEntity.ok(leaveService.getPendingMentorLeaves(mentorId));
    }

    @GetMapping("/hod/pending")
    public ResponseEntity<List<LeaveResponseDto>> getPendingHodLeaves() {
        return ResponseEntity.ok(leaveService.getPendingHodLeaves());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveResponseDto> getLeaveById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.getLeaveById(id));
    }

    @GetMapping
    public ResponseEntity<List<LeaveResponseDto>> getAllLeaves() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }
}
