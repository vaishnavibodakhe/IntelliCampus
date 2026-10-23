package com.intellicampus.academic.controller;

import com.intellicampus.academic.dto.DepartmentDto;
import com.intellicampus.academic.dto.DivisionDto;
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

    // ==========================================
    // Rooms
    // ==========================================
    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<RoomDto>> listRooms() {
        return ResponseEntity.ok(academicSetupService.getAllRooms());
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<RoomDto> createRoom(@RequestBody RoomDto dto) {
        RoomDto created = academicSetupService.createRoom(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/rooms/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<RoomDto> updateRoom(@PathVariable Long id, @RequestBody RoomDto dto) {
        RoomDto updated = academicSetupService.updateRoom(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/rooms/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        academicSetupService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Subjects
    // ==========================================
    @GetMapping("/subjects")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<SubjectDto>> listSubjects() {
        return ResponseEntity.ok(academicSetupService.getAllSubjects());
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<SubjectDto> createSubject(@RequestBody SubjectDto dto) {
        SubjectDto created = academicSetupService.createSubject(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/subjects/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<SubjectDto> updateSubject(@PathVariable Long id, @RequestBody SubjectDto dto) {
        SubjectDto updated = academicSetupService.updateSubject(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/subjects/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        academicSetupService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Departments
    // ==========================================
    @GetMapping("/departments")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<DepartmentDto>> listDepartments() {
        return ResponseEntity.ok(academicSetupService.getAllDepartments());
    }

    @PostMapping("/departments")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<DepartmentDto> createDepartment(@RequestBody DepartmentDto dto) {
        DepartmentDto created = academicSetupService.createDepartment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<DepartmentDto> updateDepartment(@PathVariable Long id, @RequestBody DepartmentDto dto) {
        DepartmentDto updated = academicSetupService.updateDepartment(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        academicSetupService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Divisions / Classes
    // ==========================================
    @GetMapping("/divisions")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN', 'HOD', 'FACULTY', 'MENTOR')")
    public ResponseEntity<List<DivisionDto>> listDivisions() {
        return ResponseEntity.ok(academicSetupService.getAllDivisions());
    }

    @PostMapping("/divisions")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<DivisionDto> createDivision(@RequestBody DivisionDto dto) {
        DivisionDto created = academicSetupService.createDivision(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/divisions/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<DivisionDto> updateDivision(@PathVariable Long id, @RequestBody DivisionDto dto) {
        DivisionDto updated = academicSetupService.updateDivision(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/divisions/{id}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'ADMIN')")
    public ResponseEntity<Void> deleteDivision(@PathVariable Long id) {
        academicSetupService.deleteDivision(id);
        return ResponseEntity.noContent().build();
    }
}
