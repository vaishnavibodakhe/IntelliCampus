package com.intellicampus.security.config;

import com.intellicampus.security.jwt.CustomAccessDeniedHandler;
import com.intellicampus.security.jwt.JwtAuthenticationEntryPoint;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174,http://localhost:3000}")
    private String allowedOrigins;

    @org.springframework.beans.factory.annotation.Autowired
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JwtAuthenticationEntryPoint authenticationEntryPoint,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CustomAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint != null ? authenticationEntryPoint : new JwtAuthenticationEntryPoint();
        this.accessDeniedHandler = accessDeniedHandler != null ? accessDeniedHandler : new CustomAccessDeniedHandler();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Full authentication is required to access this resource\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Access is denied: ADMIN authority required\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Allow CORS pre-flight requests
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ADMIN only endpoints for staff approvals (evaluated before public /api/auth/**)
                .requestMatchers(
                    "/api/auth/staff/pending",
                    "/api/auth/staff/pending/**",
                    "/api/auth/staff/*/approve",
                    "/api/auth/staff/*/reject",
                    "/api/auth/staff/{id}/approve",
                    "/api/auth/staff/{id}/reject"
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

                // Biometric face enrollment and capture-token endpoints
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

                // Faculty & Mentor auth endpoints
                .requestMatchers(
                    "/api/faculty/login",
                    "/api/faculty/register",
                    "/api/faculty/forgot-password",
                    "/api/faculty/verify-otp",
                    "/api/faculty/reset-password",
                    "/api/faculty/reset-password/**",
                    "/api/mentor/login",
                    "/api/mentor/register",
                    "/api/mentor/forgot-password",
                    "/api/mentor/verify-otp",
                    "/api/mentor/reset-password",
                    "/api/mentor/reset-password/**",
                    "/api/logout"
                ).permitAll()

                // Public verification & Swagger docs
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                .requestMatchers("/api/public/**", "/api/credentials/verify/**", "/api/credentials/public/**").permitAll()

                // Public auth endpoints
                .requestMatchers("/api/auth/**").permitAll()

                // Role-specific protected routes
                .requestMatchers("/api/faculty/**").hasRole("FACULTY")
                .requestMatchers("/api/mentor/**").hasRole("MENTOR")

                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
