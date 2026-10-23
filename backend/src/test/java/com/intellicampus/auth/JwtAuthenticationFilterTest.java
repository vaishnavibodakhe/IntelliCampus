package com.intellicampus.auth;

import com.intellicampus.auth.config.JwtAuthenticationFilter;
import com.intellicampus.auth.service.JwtTokenProvider;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.TokenDenylistRepository;
import com.intellicampus.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenDenylistRepository tokenDenylistRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Allows active staff user with valid JWT token to authenticate")
    void testActiveUserAuthenticated() throws Exception {
        String token = "valid.staff.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenDenylistRepository.existsByToken(token)).thenReturn(false);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn("faculty@intellicampus.edu");
        when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("FACULTY");

        User activeUser = new User("FAC-01", Role.FACULTY, "Prof active", "faculty@intellicampus.edu", "hash", null);
        activeUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmail("faculty@intellicampus.edu")).thenReturn(Optional.of(activeUser));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(activeUser, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Deactivated staff account (DISABLED) has existing tokens blocked on every request")
    void testDeactivatedStaffTokenBlocked() throws Exception {
        String token = "existing.staff.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenDenylistRepository.existsByToken(token)).thenReturn(false);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn("disabled@intellicampus.edu");

        User disabledUser = new User("FAC-02", Role.FACULTY, "Prof disabled", "disabled@intellicampus.edu", "hash", null);
        disabledUser.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmail("disabled@intellicampus.edu")).thenReturn(Optional.of(disabledUser));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Security context must remain unauthenticated for disabled users
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
