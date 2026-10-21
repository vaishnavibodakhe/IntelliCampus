package com.intellicampus.security.config;

import com.intellicampus.security.jwt.CustomAccessDeniedHandler;
import com.intellicampus.security.jwt.JwtAuthenticationEntryPoint;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final Environment environment;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174,http://localhost:3000}")
    private String allowedOrigins;

    @org.springframework.beans.factory.annotation.Autowired
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JwtAuthenticationEntryPoint authenticationEntryPoint,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CustomAccessDeniedHandler accessDeniedHandler,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint != null ? authenticationEntryPoint : new JwtAuthenticationEntryPoint();
        this.accessDeniedHandler = accessDeniedHandler != null ? accessDeniedHandler : new CustomAccessDeniedHandler();
        this.environment = environment;
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
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("*"))
                .collect(Collectors.toList());
        configuration.setAllowedOrigins(origins);
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
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'"))
            )
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
                    response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Access denied: insufficient permissions for this resource\"}");
                })
            )
            .authorizeHttpRequests(auth -> {
                // Allow CORS pre-flight requests
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                // Swagger / OpenAPI documentation (allowed in dev profile or error)
                boolean isDev = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("dev");
                if (isDev) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                }
                auth.requestMatchers("/error").permitAll();

                // 1. PUBLIC AUTH & VERIFICATION ENDPOINTS
                auth.requestMatchers(
                    "/api/auth/admin/login",
                    "/api/auth/admin/verify-otp",
                    "/api/auth/admin/register",
                    "/api/auth/staff/login",
                    "/api/auth/student/login",
                    "/api/auth/login",
                    "/api/auth/student/register",
                    "/api/auth/register",
                    "/api/faculty/login",
                    "/api/auth/faculty/login",
                    "/api/mentor/login",
                    "/api/auth/mentor/login",
                    "/api/auth/staff/refresh",
                    "/api/auth/staff/forgot-password",
                    "/api/auth/staff/reset-password",
                    "/api/auth/staff/verify-otp",
                    "/api/faculty/forgot-password",
                    "/api/faculty/verify-otp",
                    "/api/faculty/reset-password",
                    "/api/faculty/reset-password/**",
                    "/api/auth/faculty/forgot-password",
                    "/api/auth/faculty/verify-otp",
                    "/api/auth/faculty/reset-password",
                    "/api/auth/faculty/reset-password/**",
                    "/api/mentor/forgot-password",
                    "/api/mentor/verify-otp",
                    "/api/mentor/reset-password",
                    "/api/mentor/reset-password/**",
                    "/api/auth/mentor/forgot-password",
                    "/api/auth/mentor/verify-otp",
                    "/api/auth/mentor/reset-password",
                    "/api/auth/mentor/reset-password/**",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/auth/validate-reset-token",
                    "/api/logout",
                    "/api/auth/logout",
                    "/api/auth/student/logout",
                    "/api/public/**",
                    "/api/credentials/verify/**",
                    "/api/credentials/public/**"
                ).permitAll();

                // Student face enrollment and one-time capture tokens during registration
                auth.requestMatchers(
                    HttpMethod.POST,
                    "/api/students/*/capture-token",
                    "/api/students/*/face-enroll",
                    "/api/students/{studentId}/capture-token",
                    "/api/students/{studentId}/face-enroll"
                ).permitAll();

                // 2. ADMIN ONLY ENDPOINTS
                auth.requestMatchers(
                    "/api/admin/**",
                    "/api/users/**",
                    "/api/audit/**",
                    "/api/auth/staff/pending",
                    "/api/auth/staff/pending/**",
                    "/api/auth/staff/*/approve",
                    "/api/auth/staff/*/reject",
                    "/api/auth/staff/{id}/approve",
                    "/api/auth/staff/{id}/reject"
                ).hasRole("ADMIN");

                auth.requestMatchers(
                    HttpMethod.POST,
                    "/api/academic/rooms",
                    "/api/academic/rooms/**",
                    "/api/academic/subjects",
                    "/api/academic/subjects/**",
                    "/api/credentials/issue"
                ).hasRole("ADMIN");

                auth.requestMatchers(
                    HttpMethod.PUT,
                    "/api/credentials/*/revoke",
                    "/api/credentials/{code}/revoke"
                ).hasRole("ADMIN");

                // 3. ADMIN + HOD ENDPOINTS
                auth.requestMatchers(
                    "/api/marks/corrections/*/decision",
                    "/api/marks/corrections/{id}/decision",
                    "/api/marks/corrections/pending",
                    "/api/leaves/*/hod",
                    "/api/leaves/{id}/hod",
                    "/api/leaves/hod/pending",
                    "/api/credentials"
                ).hasAnyRole("ADMIN", "HOD");

                // 4. ADMIN + HOD + MENTOR ENDPOINTS
                auth.requestMatchers(
                    "/api/leaves/*/mentor",
                    "/api/leaves/{id}/mentor",
                    "/api/leaves/mentor/*/pending",
                    "/api/leaves/mentor/{mentorId}/pending",
                    "/api/mentor/**"
                ).hasAnyRole("ADMIN", "HOD", "MENTOR");

                // 5. ADMIN + HOD + FACULTY ENDPOINTS
                auth.requestMatchers(
                    "/api/faculty/**",
                    "/api/attendance/sessions",
                    "/api/attendance/sessions/**",
                    "/api/marks",
                    "/api/marks/corrections",
                    "/api/marks/subject/**"
                ).hasAnyRole("ADMIN", "HOD", "FACULTY");

                // 6. STAFF ROLES (ADMIN, HOD, FACULTY, MENTOR)
                auth.requestMatchers(
                    "/api/students/division/**",
                    "/api/students/division/{divisionId}",
                    "/api/academic/rooms/**",
                    "/api/academic/subjects/**",
                    "/api/ai/risk/high",
                    "/api/ai/risk/evaluate",
                    "/api/ai/risk/student/**",
                    "/api/leaves"
                ).hasAnyRole("ADMIN", "HOD", "FACULTY", "MENTOR");

                auth.requestMatchers(
                    HttpMethod.POST,
                    "/api/notices"
                ).hasAnyRole("ADMIN", "HOD", "FACULTY");

                // 7. ALL AUTHENTICATED ROLES (Specific verified endpoints)
                auth.requestMatchers(
                    "/api/notices/**",
                    "/api/auth/staff/me",
                    "/api/auth/staff/change-password",
                    "/api/auth/student/me",
                    "/api/students/{studentId}",
                    "/api/marks/student/**",
                    "/api/leaves/student/**",
                    "/api/leaves/{id}",
                    "/api/attendance/sessions/active",
                    "/api/attendance/sessions/{id}",
                    "/api/attendance/sessions/records/student/**",
                    "/api/credentials/student/**"
                ).authenticated();

                auth.requestMatchers(
                    HttpMethod.POST,
                    "/api/leaves"
                ).authenticated();

                // 8. STRICT DEFAULT DENY
                auth.requestMatchers("/api/**").denyAll();
                auth.anyRequest().denyAll();
            });

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
