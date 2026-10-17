package com.intellicampus.user.controller;

import com.intellicampus.academic.model.Subject;
import com.intellicampus.academic.repository.SubjectRepository;
import com.intellicampus.academics.model.MarkCorrectionRequest;
import com.intellicampus.academics.repository.MarkCorrectionRequestRepository;
import com.intellicampus.attendance.model.AttendanceSession;
import com.intellicampus.attendance.repository.AttendanceSessionRepository;
import com.intellicampus.user.dto.ApiResponse;
import com.intellicampus.user.dto.UserResponseDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/faculty")
@PreAuthorize("hasRole('FACULTY')")
public class FacultyPortalController {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final MarkCorrectionRequestRepository markCorrectionRequestRepository;

    public FacultyPortalController(UserRepository userRepository,
                                   SubjectRepository subjectRepository,
                                   AttendanceSessionRepository attendanceSessionRepository,
                                   MarkCorrectionRequestRepository markCorrectionRequestRepository) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.markCorrectionRequestRepository = markCorrectionRequestRepository;
    }

    private User getAuthenticatedFaculty(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated access");
        }
        String principal = authentication.getName();
        return userRepository.findByEmailIgnoreCase(principal)
                .or(() -> userRepository.findByUserCode(principal))
                .orElseThrow(() -> new RuntimeException("Faculty account not found"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDto>> getCurrentFacultyProfile(Authentication authentication) {
        User faculty = getAuthenticatedFaculty(authentication);

        UserResponseDto dto = new UserResponseDto();
        dto.setId(faculty.getId());
        dto.setUserCode(faculty.getUserCode());
        dto.setName(faculty.getName());
        dto.setEmail(faculty.getEmail());
        dto.setRole(faculty.getRole());
        dto.setDepartment(faculty.getDepartment() != null ? faculty.getDepartment().getName() : "Computer Science");
        dto.setStatus(faculty.getStatus());
        dto.setPhone(faculty.getPhone());

        return ResponseEntity.ok(ApiResponse.success("Faculty profile loaded", dto));
    }

    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAssignedCourses(Authentication authentication) {
        User faculty = getAuthenticatedFaculty(authentication);
        List<Subject> subjects = subjectRepository.findByFaculty(faculty);

        List<Map<String, Object>> result = subjects.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("code", s.getCode());
            map.put("name", s.getName());
            map.put("credits", s.getCredits());
            map.put("department", s.getDepartment() != null ? s.getDepartment().getName() : "");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Assigned courses retrieved", result));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAttendanceSessions(Authentication authentication) {
        User faculty = getAuthenticatedFaculty(authentication);
        List<AttendanceSession> sessions = attendanceSessionRepository.findByFacultyOrderByStartedAtDesc(faculty);

        List<Map<String, Object>> result = sessions.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("challengeCode", s.getChallengeCode());
            map.put("status", s.getStatus().name());
            map.put("startedAt", s.getStartedAt());
            map.put("subjectCode", s.getSubject() != null ? s.getSubject().getCode() : "");
            map.put("subjectName", s.getSubject() != null ? s.getSubject().getName() : "");
            map.put("division", s.getDivision() != null ? s.getDivision().getName() : "");
            map.put("room", s.getRoom() != null ? s.getRoom().getName() : "");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Faculty attendance sessions retrieved", result));
    }

    @GetMapping("/corrections")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMarkCorrections(Authentication authentication) {
        User faculty = getAuthenticatedFaculty(authentication);
        List<MarkCorrectionRequest> requests = markCorrectionRequestRepository.findByFacultyOrderByRequestedAtDesc(faculty);

        List<Map<String, Object>> result = requests.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("oldMarks", r.getOldMarks());
            map.put("newMarks", r.getNewMarks());
            map.put("reason", r.getReason());
            map.put("status", r.getStatus().name());
            map.put("requestedAt", r.getRequestedAt());
            map.put("decisionComment", r.getDecisionComment());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Faculty mark corrections retrieved", result));
    }
}
