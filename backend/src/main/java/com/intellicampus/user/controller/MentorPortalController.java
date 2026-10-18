package com.intellicampus.user.controller;

import com.intellicampus.leave.model.LeaveRequest;
import com.intellicampus.leave.repository.LeaveRequestRepository;
import com.intellicampus.user.dto.ApiResponse;
import com.intellicampus.user.dto.UserResponseDto;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
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
@RequestMapping("/api/mentor")
@PreAuthorize("hasRole('MENTOR')")
public class MentorPortalController {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public MentorPortalController(UserRepository userRepository,
                                  StudentProfileRepository studentProfileRepository,
                                  LeaveRequestRepository leaveRequestRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    private User getAuthenticatedMentor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Unauthenticated access");
        }
        String principal = authentication.getName();
        return userRepository.findByEmailIgnoreCase(principal)
                .or(() -> userRepository.findByUserCode(principal))
                .orElseThrow(() -> new RuntimeException("Mentor account not found"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDto>> getCurrentMentorProfile(Authentication authentication) {
        User mentor = getAuthenticatedMentor(authentication);

        UserResponseDto dto = new UserResponseDto();
        dto.setId(mentor.getId());
        dto.setUserCode(mentor.getUserCode());
        dto.setName(mentor.getName());
        dto.setEmail(mentor.getEmail());
        dto.setRole(mentor.getRole());
        dto.setDepartment(mentor.getDepartment() != null ? mentor.getDepartment().getName() : "Faculty Mentor");
        dto.setStatus(mentor.getStatus());
        dto.setPhone(mentor.getPhone());
        dto.setEmployeeId(mentor.getUserCode());
        dto.setSpecialization(mentor.getSpecialization());

        return ResponseEntity.ok(ApiResponse.success("Mentor profile loaded", dto));
    }

    @GetMapping("/mentees")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAssignedMentees(Authentication authentication) {
        User mentor = getAuthenticatedMentor(authentication);
        List<StudentProfile> mentees = studentProfileRepository.findByMentor(mentor);

        List<Map<String, Object>> result = mentees.stream().map(sp -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", sp.getId());
            map.put("rollNo", sp.getRollNo());
            map.put("name", sp.getUser() != null ? sp.getUser().getName() : "");
            map.put("email", sp.getUser() != null ? sp.getUser().getEmail() : "");
            map.put("attendanceRate", sp.getAttendanceRate());
            map.put("backlogs", sp.getBacklogs());
            map.put("feeStatus", sp.getFeeStatus());
            map.put("dueAmount", sp.getDueAmount());
            map.put("division", sp.getDivision() != null ? sp.getDivision().getName() : "");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Assigned mentees retrieved", result));
    }

    @GetMapping("/leaves")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAssignedLeaves(Authentication authentication) {
        User mentor = getAuthenticatedMentor(authentication);
        List<LeaveRequest> requests = leaveRequestRepository.findByMentorOrderBySubmittedAtDesc(mentor);

        List<Map<String, Object>> result = requests.stream().map(lr -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", lr.getId());
            map.put("studentRollNo", lr.getStudent() != null ? lr.getStudent().getRollNo() : "");
            map.put("studentName", lr.getStudent() != null && lr.getStudent().getUser() != null ? lr.getStudent().getUser().getName() : "");
            map.put("fromDate", lr.getFromDate());
            map.put("toDate", lr.getToDate());
            map.put("daysCount", lr.getDaysCount());
            map.put("reason", lr.getReason());
            map.put("mentorStatus", lr.getMentorStatus().name());
            map.put("mentorComment", lr.getMentorComment());
            map.put("finalStatus", lr.getFinalStatus().name());
            map.put("submittedAt", lr.getSubmittedAt());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Assigned leave requests retrieved", result));
    }
}
