package com.intellicampus.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Enforce BCrypt strength 12
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Full authentication is required to access this resource\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Access is denied: ADMIN authority required\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // ADMIN only endpoints for staff management & approvals (evaluated before public /api/auth/**)
                .requestMatchers(
                    "/api/auth/staff/pending",
                    "/api/auth/staff/pending/**",
                    "/api/auth/staff/*/approve",
                    "/api/auth/staff/*/reject",
                    "/api/auth/staff/{id}/approve",
                    "/api/auth/staff/{id}/reject",
                    "/api/auth/admin/staff",
                    "/api/auth/admin/staff/**",
                    "/api/auth/staff/staff",
                    "/api/auth/staff/staff/**"
                ).hasRole("ADMIN")
                // Authenticated staff endpoints (staff only: ADMIN, HOD, FACULTY, MENTOR)
                .requestMatchers("/api/auth/staff/me", "/api/auth/staff/change-password").hasAnyRole("ADMIN", "HOD", "FACULTY", "MENTOR")
                // Public student auth endpoints
                .requestMatchers(
                    "/api/auth/student/register",
                    "/api/auth/student/login",
                    "/api/auth/student/logout",
                    "/api/auth/student/me"
                ).permitAll()
                // Biometric face enrollment and capture-token endpoints (permitAll ONLY for these two POST endpoints)
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/students/*/capture-token",
                    "/api/students/*/face-enroll",
                    "/api/students/{studentId}/capture-token",
                    "/api/students/{studentId}/face-enroll"
                ).permitAll()
                // Division list restricted to staff (ADMIN, HOD, FACULTY, MENTOR)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/students/division/**",
                    "/api/students/division/{divisionId}"
                ).hasAnyRole("ADMIN", "HOD", "FACULTY", "MENTOR")
                // All other /api/students/** require authentication
                .requestMatchers("/api/students/**").authenticated()
                // Public auth endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/credentials/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("http://localhost:*", "http://127.0.0.1:*", "*"));
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174",
            "http://localhost:3000"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
