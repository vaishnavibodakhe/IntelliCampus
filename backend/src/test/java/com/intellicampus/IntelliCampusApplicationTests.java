package com.intellicampus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IntelliCampusApplicationTests {

    @Test
    @DisplayName("Context Loads: Verifies complete Spring Boot application context boots without bean collisions or missing dependencies")
    void contextLoads() {
        // If there are duplicate bean definitions (e.g. conflicting SecurityConfig) or invalid wiring, context load will fail.
    }
}
