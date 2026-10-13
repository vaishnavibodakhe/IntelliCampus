package com.intellicampus.academic.controller;

import com.intellicampus.academic.dto.RoomDto;
import com.intellicampus.academic.dto.SubjectDto;
import com.intellicampus.academic.service.AcademicSetupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<RoomDto>> listRooms() {
        return ResponseEntity.ok(academicSetupService.getAllRooms());
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomDto> createRoom(@RequestBody RoomDto dto) {
        RoomDto created = academicSetupService.createRoom(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectDto>> listSubjects() {
        return ResponseEntity.ok(academicSetupService.getAllSubjects());
    }

    @PostMapping("/subjects")
    public ResponseEntity<SubjectDto> createSubject(@RequestBody SubjectDto dto) {
        SubjectDto created = academicSetupService.createSubject(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
