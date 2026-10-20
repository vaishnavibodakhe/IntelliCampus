package com.intellicampus.user.service;

import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Spring context integration test verifying that Spring's IoC container
 * resolves the @Autowired 8-argument constructor of FaceEnrollmentService
 * and correctly injects all required service and repository dependencies.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        FaceEnrollmentService.class,
        FaceEnrollmentServiceSpringContextTest.TestConfig.class
})
class FaceEnrollmentServiceSpringContextTest {

    @Configuration
    static class TestConfig {

        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public StudentProfileRepository studentProfileRepository() {
            return Mockito.mock(StudentProfileRepository.class);
        }

        @Bean
        public UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        public AuditChainService auditChainService() {
            return Mockito.mock(AuditChainService.class);
        }

        @Bean
        public IpRateLimiterService ipRateLimiterService() {
            return Mockito.mock(IpRateLimiterService.class);
        }
    }

    @Autowired
    private FaceEnrollmentService faceEnrollmentService;

    @Test
    @DisplayName("Verify Spring context selects @Autowired 8-argument constructor and dependencies are non-null")
    void testInjectedFaceEnrollmentServiceHasNonNullDependencies() {
        assertNotNull(faceEnrollmentService, "FaceEnrollmentService bean should be injected by Spring context");
        assertNotNull(faceEnrollmentService.getIpRateLimiterService(), "ipRateLimiterService must not be null");
        assertNotNull(faceEnrollmentService.getStudentProfileRepository(), "studentProfileRepository must not be null");
        assertNotNull(faceEnrollmentService.getUserRepository(), "userRepository must not be null");
        assertNotNull(faceEnrollmentService.getAuditChainService(), "auditChainService must not be null");

        // Verify fields directly via reflection
        assertNotNull(ReflectionTestUtils.getField(faceEnrollmentService, "ipRateLimiterService"),
                "ipRateLimiterService field should not be null");
        assertNotNull(ReflectionTestUtils.getField(faceEnrollmentService, "studentProfileRepository"),
                "studentProfileRepository field should not be null");
        assertNotNull(ReflectionTestUtils.getField(faceEnrollmentService, "userRepository"),
                "userRepository field should not be null");
        assertNotNull(ReflectionTestUtils.getField(faceEnrollmentService, "auditChainService"),
                "auditChainService field should not be null");
    }
}
