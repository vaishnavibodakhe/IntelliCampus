package com.intellicampus.auth;

import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.auth.controller.StaffAuthController;
import com.intellicampus.auth.dto.PendingStaffApprovalDto;
import com.intellicampus.auth.service.JwtTokenProvider;
import com.intellicampus.auth.service.StaffAuthService;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffAuthController.class)
@Import(SecurityConfig.class)
class StaffAuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaffAuthService staffAuthService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.ServletRequest request = invocation.getArgument(0);
            jakarta.servlet.ServletResponse response = invocation.getArgument(1);
            jakarta.servlet.FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("Unauthenticated request to /pending returns 401 Unauthorized")
    void testPendingUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/staff/pending")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "hod.cs@intellicampus.edu", roles = {"HOD"})
    @DisplayName("HOD request to /pending returns 403 Forbidden")
    void testPendingHodReturns403() throws Exception {
        mockMvc.perform(get("/api/auth/staff/pending")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "principal@college.edu", roles = {"PRINCIPAL"})
    @DisplayName("Principal request to /pending returns 200 OK")
    void testPendingAdminReturns200() throws Exception {
        when(staffAuthService.getPendingApprovals()).thenReturn(List.of(
                new PendingStaffApprovalDto(1L, "HOD-01", "Dr. Turing", "turing@intellicampus.edu", Role.HOD, "CS", UserStatus.PENDING_APPROVAL, LocalDateTime.now())
        ));

        mockMvc.perform(get("/api/auth/staff/pending")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated request to approve staff returns 401 Unauthorized")
    void testApproveUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/staff/10/approve")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "hod.cs@intellicampus.edu", roles = {"HOD"})
    @DisplayName("HOD request to approve staff returns 403 Forbidden")
    void testApproveHodReturns403() throws Exception {
        mockMvc.perform(post("/api/auth/staff/10/approve")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "principal@college.edu", roles = {"PRINCIPAL"})
    @DisplayName("Principal request to approve staff returns 200 OK")
    void testApproveAdminReturns200() throws Exception {
        doNothing().when(staffAuthService).approveStaff(eq(10L), any());

        mockMvc.perform(post("/api/auth/staff/10/approve")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated request to reject staff returns 401 Unauthorized")
    void testRejectUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/staff/10/reject")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "hod.cs@intellicampus.edu", roles = {"HOD"})
    @DisplayName("HOD request to reject staff returns 403 Forbidden")
    void testRejectHodReturns403() throws Exception {
        mockMvc.perform(post("/api/auth/staff/10/reject")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "principal@college.edu", roles = {"PRINCIPAL"})
    @DisplayName("Principal request to reject staff returns 200 OK")
    void testRejectAdminReturns200() throws Exception {
        doNothing().when(staffAuthService).rejectStaff(eq(10L), any(), any());

        mockMvc.perform(post("/api/auth/staff/10/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Incomplete profile\"}"))
                .andExpect(status().isOk());
    }
}
