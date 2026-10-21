package com.intellicampus.academic.controller;

import com.intellicampus.academic.dto.RoomDto;
import com.intellicampus.academic.dto.SubjectDto;
import com.intellicampus.academic.service.AcademicSetupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/academic")
@CrossOrigin(origins = "*")
public class AcademicSetupController {

    private final AcademicSetupService academicSetupService;

    public AcademicSetupController(AcademicSetupService academicSetupService) {
        this.academicSetupService = academicSetupService;
    }

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<RoomDto>> listRooms() {
        return ResponseEntity.ok(academicSetupService.getAllRooms());
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomDto> createRoom(@RequestBody RoomDto dto) {
        RoomDto created = academicSetupService.createRoom(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/subjects")
    @PreAuthorize("hasAnyRole('ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<SubjectDto>> listSubjects() {
        return ResponseEntity.ok(academicSetupService.getAllSubjects());
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectDto> createSubject(@RequestBody SubjectDto dto) {
        SubjectDto created = academicSetupService.createSubject(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
