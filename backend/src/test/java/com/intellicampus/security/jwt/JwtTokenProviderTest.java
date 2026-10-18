package com.intellicampus.security.jwt;

import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String VALID_TEST_SECRET = "unit_test_jwt_secret_key_minimum_32_chars_for_hmac_sha256";

    @Test
    @DisplayName("Should successfully initialize with valid 32+ character secret and validate token")
    void testValidSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(VALID_TEST_SECRET, 15);
        User user = new User();
        user.setId(1L);
        user.setEmail("test@intellicampus.edu");
        user.setUserCode("STU001");
        user.setName("Test Student");
        user.setRole(Role.STUDENT);

        String token = provider.generateToken(user);

        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertEquals("test@intellicampus.edu", provider.getEmailFromToken(token));
        assertEquals("STUDENT", provider.getRoleFromToken(token));
        assertEquals(1L, provider.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("Should fail with clear message when JWT_SECRET is null")
    void testMissingSecretNull() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            new JwtTokenProvider(null, 15);
        });
        assertTrue(exception.getMessage().contains("Missing required configuration: JWT_SECRET"));
    }

    @Test
    @DisplayName("Should fail with clear message when JWT_SECRET is empty or whitespace")
    void testMissingSecretBlank() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            new JwtTokenProvider("   ", 15);
        });
        assertTrue(exception.getMessage().contains("Missing required configuration: JWT_SECRET"));
    }

    @Test
    @DisplayName("Should fail with clear message when JWT_SECRET is shorter than 32 characters")
    void testShortSecret() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            new JwtTokenProvider("too_short_secret", 15);
        });
        assertTrue(exception.getMessage().contains("JWT_SECRET is too short"));
    }
}
