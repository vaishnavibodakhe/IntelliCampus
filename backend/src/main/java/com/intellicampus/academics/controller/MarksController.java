package com.intellicampus.academics.controller;

import com.intellicampus.academics.dto.*;
import com.intellicampus.academics.service.MarksService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/marks")
@CrossOrigin(origins = "*")
public class MarksController {

    private final MarksService marksService;

    public MarksController(MarksService marksService) {
        this.marksService = marksService;
    }

    @PostMapping
    public ResponseEntity<MarkResponseDto> saveOrUpdateMark(@RequestBody SaveMarkRequestDto req) {
        MarkResponseDto saved = marksService.saveOrUpdateMark(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<MarkResponseDto>> getStudentMarks(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "true") boolean onlyPublished) {
        return ResponseEntity.ok(marksService.getMarksForStudent(studentId, onlyPublished));
    }

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<MarkResponseDto>> getSubjectMarks(@PathVariable Long subjectId) {
        return ResponseEntity.ok(marksService.getMarksForSubject(subjectId));
    }

    @PostMapping("/corrections")
    public ResponseEntity<CorrectionResponseDto> requestCorrection(@RequestBody SubmitCorrectionRequestDto req) {
        CorrectionResponseDto created = marksService.submitCorrectionRequest(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/corrections/pending")
    public ResponseEntity<List<CorrectionResponseDto>> getPendingCorrections() {
        return ResponseEntity.ok(marksService.getPendingCorrections());
    }

    @PutMapping("/corrections/{id}/decision")
    public ResponseEntity<CorrectionResponseDto> decideCorrection(
            @PathVariable Long id,
            @RequestBody CorrectionDecisionDto decision) {
        return ResponseEntity.ok(marksService.decideCorrection(id, decision));
    }
}
