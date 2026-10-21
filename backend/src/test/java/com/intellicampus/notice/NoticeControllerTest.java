package com.intellicampus.notice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellicampus.notice.controller.NoticeController;
import com.intellicampus.notice.dto.CreateNoticeRequestDto;
import com.intellicampus.notice.dto.NoticeDto;
import com.intellicampus.notice.service.NoticeService;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NoticeControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private NoticeService noticeService;

    @InjectMocks
    private NoticeController noticeController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(noticeController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("GET /api/notices/my-feed with query param userId returns feed")
    void testGetFeedWithQueryParam() throws Exception {
        NoticeDto dto = new NoticeDto();
        dto.setId(1L);
        dto.setTitle("Campus Holiday");
        dto.setBody("Classes suspended");
        dto.setRead(false);
        dto.setPublisherRole("ADMIN");

        when(noticeService.getFeedForUser(101L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/notices/my-feed?userId=101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].title").value("Campus Holiday"))
                .andExpect(jsonPath("$[0].read").value(false));

        verify(noticeService).getFeedForUser(101L);
    }

    @Test
    @DisplayName("GET /api/notices/my-feed with authenticated student enforces security context user ID")
    void testGetFeedWithAuthenticatedStudentOverridesParam() throws Exception {
        User student = new User("STU-100", Role.STUDENT, "Aarav", "aarav@test.com", "pass", null);
        org.springframework.test.util.ReflectionTestUtils.setField(student, "id", 55L);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                student,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        NoticeDto dto = new NoticeDto();
        dto.setId(2L);
        dto.setTitle("Exam Notice");
        dto.setRead(true);

        when(noticeService.getFeedForUser(55L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/notices/my-feed?userId=999").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[0].read").value(true));

        // Note: Despite userId=999 in param, student authentication strictly enforces student's own ID 55
        verify(noticeService).getFeedForUser(55L);
    }

    @Test
    @DisplayName("POST /api/notices/{id}/read marks notice as read")
    void testMarkAsRead() throws Exception {
        when(noticeService.markAsRead(1L, 101L)).thenReturn(true);

        mockMvc.perform(post("/api/notices/1/read?userId=101"))
                .andExpect(status().isOk());

        verify(noticeService).markAsRead(1L, 101L);
    }

    @Test
    @DisplayName("POST /api/notices/{id}/read returns 204 if already marked read")
    void testMarkAsReadAlreadyRead() throws Exception {
        when(noticeService.markAsRead(1L, 101L)).thenReturn(false);

        mockMvc.perform(post("/api/notices/1/read?userId=101"))
                .andExpect(status().isNoContent());

        verify(noticeService).markAsRead(1L, 101L);
    }

    @Test
    @DisplayName("GET /api/notices/my-feed without userId or auth returns 400 Bad Request")
    void testGetFeedWithoutUserOrAuth() throws Exception {
        mockMvc.perform(get("/api/notices/my-feed"))
                .andExpect(status().isBadRequest());
    }
}
