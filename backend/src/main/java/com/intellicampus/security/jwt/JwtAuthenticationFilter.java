package com.intellicampus.security.jwt;

import com.intellicampus.user.repository.RevokedTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final RevokedTokenRepository revokedTokenRepository;
    private final com.intellicampus.user.repository.UserRepository userRepository;
    private final com.intellicampus.user.repository.TokenDenylistRepository tokenDenylistRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RevokedTokenRepository revokedTokenRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.intellicampus.user.repository.UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.intellicampus.user.repository.TokenDenylistRepository tokenDenylistRepository) {
        this.tokenProvider = tokenProvider;
        this.revokedTokenRepository = revokedTokenRepository;
        this.userRepository = userRepository;
        this.tokenDenylistRepository = tokenDenylistRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                // 1. Check student token denylist
                if (tokenDenylistRepository != null && tokenDenylistRepository.existsByToken(jwt)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                if (tokenProvider.validateToken(jwt)) {
                    // 2. Check faculty/mentor revoked token repository
                    String tokenHash = DigestUtils.sha256Hex(jwt);
                    if (revokedTokenRepository == null || !revokedTokenRepository.existsByTokenHash(tokenHash)) {
                        String email = tokenProvider.getEmailFromToken(jwt);
                        String role = tokenProvider.getRoleFromToken(jwt);

                        if (role != null) {
                            String roleAuthority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleAuthority);

                            Object principal = email;
                            if (userRepository != null && email != null) {
                                java.util.Optional<com.intellicampus.user.model.User> userOpt = userRepository.findByEmail(email);
                                if (userOpt.isPresent()) {
                                    com.intellicampus.user.model.User user = userOpt.get();
                                    if (user.getStatus() != com.intellicampus.user.model.UserStatus.ACTIVE) {
                                        filterChain.doFilter(request, response);
                                        return;
                                    }
                                    if (Boolean.TRUE.equals(user.getMustChangePassword())) {
                                        String uri = request.getRequestURI();
                                        if (!isPasswordChangeOrLogoutAllowed(uri)) {
                                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                            response.setContentType("application/json");
                                            response.setCharacterEncoding("UTF-8");
                                            response.getWriter().write("{\"error\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Password change required before accessing the application.\"}");
                                            return;
                                        }
                                    }
                                    principal = user;
                                }
                            }

                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(principal, null, Collections.singletonList(authority));
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPasswordChangeOrLogoutAllowed(String uri) {
        if (uri == null) {
            return false;
        }
        String lower = uri.toLowerCase();
        return lower.contains("/change-password")
                || lower.contains("/logout")
                || lower.contains("/refresh");
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
