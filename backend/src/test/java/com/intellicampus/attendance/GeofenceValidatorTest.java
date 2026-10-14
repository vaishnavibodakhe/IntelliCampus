package com.intellicampus.attendance;

import com.intellicampus.attendance.validation.GeofenceValidationException;
import com.intellicampus.attendance.validation.GeofenceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GeofenceValidator compliant with IC-SRS-1.0 Section 8.2 and Test Case TC-02.
 * Verifies Haversine distance calculation and boundary edge cases.
 */
class GeofenceValidatorTest {

    private GeofenceValidator validator;

    // College classroom benchmark coordinates (Campus Main Hall)
    private static final double ROOM_LAT = 18.5204303;
    private static final double ROOM_LNG = 73.8567437;
    private static final int DEFAULT_RADIUS = 50; // meters

    @BeforeEach
    void setUp() {
        validator = new GeofenceValidator();
    }

    @Test
    @DisplayName("Should pass when student is inside geofence perimeter (~15m away)")
    void testInsideGeofenceBoundary() {
        // ~15 meters northeast of classroom center
        double studentLat = 18.520520;
        double studentLng = 73.856830;
        double accuracyMeters = 15.0;

        double distance = validator.validate(studentLat, studentLng, accuracyMeters, ROOM_LAT, ROOM_LNG, DEFAULT_RADIUS);

        assertTrue(distance < DEFAULT_RADIUS, "Calculated distance should be less than allowable radius");
        assertTrue(distance > 0, "Distance should be positive");
    }

    @Test
    @DisplayName("Should pass when student is directly at room center (0m away)")
    void testAtExactCenter() {
        double distance = validator.validate(ROOM_LAT, ROOM_LNG, 5.0, ROOM_LAT, ROOM_LNG, DEFAULT_RADIUS);

        assertEquals(0.0, distance, 0.001, "Distance should be 0 when coordinates match center");
    }

    @Test
    @DisplayName("Should pass when student is within or directly on boundary threshold")
    void testOnBoundaryThreshold() {
        // Distance roughly 48 meters away
        double studentLat = 18.520860;
        double studentLng = 73.8567437;
        int radius = 50;

        double distance = validator.validate(studentLat, studentLng, 20.0, ROOM_LAT, ROOM_LNG, radius);

        assertTrue(distance <= radius, "Distance should be within the allowable radius");
    }

    @Test
    @DisplayName("Should throw GeofenceValidationException when student is outside allowable boundary")
    void testOutsideGeofenceBoundary() {
        // Roughly 220 meters away
        double studentLat = 18.5224303;
        double studentLng = 73.8567437;
        double accuracyMeters = 10.0;

        GeofenceValidationException ex = assertThrows(GeofenceValidationException.class, () ->
                validator.validate(studentLat, studentLng, accuracyMeters, ROOM_LAT, ROOM_LNG, DEFAULT_RADIUS)
        );

        assertNotNull(ex.getDistanceMeters(), "Exception should report calculated distance");
        assertEquals(DEFAULT_RADIUS, ex.getAllowableRadiusMeters(), "Exception should report allowable radius");
        assertTrue(ex.getDistanceMeters() > DEFAULT_RADIUS, "Distance in exception should exceed boundary");
        assertTrue(ex.getMessage().contains("Geofence alert"), "Error message should identify geofence violation");
    }

    @Test
    @DisplayName("Should reject check-in when device GPS accuracy is worse than 100 meters")
    void testGpsAccuracyThresholdExceeded() {
        // Student is geographically inside (<10m), but GPS accuracy reported is ±125m
        double studentLat = ROOM_LAT + 0.00005;
        double studentLng = ROOM_LNG + 0.00005;
        double poorAccuracy = 125.0;

        GeofenceValidationException ex = assertThrows(GeofenceValidationException.class, () ->
                validator.validate(studentLat, studentLng, poorAccuracy, ROOM_LAT, ROOM_LNG, DEFAULT_RADIUS)
        );

        assertNotNull(ex.getAccuracyMeters(), "Exception should report the offending accuracy value");
        assertEquals(poorAccuracy, ex.getAccuracyMeters(), 0.001);
        assertTrue(ex.getMessage().contains("GPS accuracy"), "Error message should cite GPS accuracy");
    }

    @Test
    @DisplayName("Should accept check-in when device GPS accuracy is within 100 meters")
    void testGpsAccuracyWithinAllowableLimit() {
        double studentLat = ROOM_LAT + 0.00005;
        double studentLng = ROOM_LNG + 0.00005;
        double goodAccuracy = 95.0;

        assertDoesNotThrow(() ->
                validator.validate(studentLat, studentLng, goodAccuracy, ROOM_LAT, ROOM_LNG, DEFAULT_RADIUS)
        );
    }

    @Test
    @DisplayName("Should use 50m default radius when allowableRadius parameter is null or non-positive")
    void testDefaultRadiusFallback() {
        // 40m away -> passes with default 50m
        double studentLat = 18.52079;
        double studentLng = ROOM_LNG;

        double distance = validator.validate(studentLat, studentLng, 10.0, ROOM_LAT, ROOM_LNG, null);
        assertTrue(distance < 50.0);

        // 60m away -> fails with default 50m
        double farLat = 18.52097;
        assertThrows(GeofenceValidationException.class, () ->
                validator.validate(farLat, ROOM_LNG, 10.0, ROOM_LAT, ROOM_LNG, null)
        );
    }

    @Test
    @DisplayName("Should throw exception when student coordinates are missing")
    void testMissingStudentCoordinates() {
        GeofenceValidationException ex = assertThrows(GeofenceValidationException.class, () ->
                validator.validate(null, ROOM_LNG, 10.0, ROOM_LAT, ROOM_LNG, DEFAULT_RADIUS)
        );
        assertTrue(ex.getMessage().contains("mandatory"));
    }

    @Test
    @DisplayName("Should throw exception when classroom coordinates are not configured")
    void testMissingRoomCoordinates() {
        GeofenceValidationException ex = assertThrows(GeofenceValidationException.class, () ->
                validator.validate(ROOM_LAT, ROOM_LNG, 10.0, null, ROOM_LNG, DEFAULT_RADIUS)
        );
        assertTrue(ex.getMessage().contains("not configured"));
    }

    @ParameterizedTest(name = "Haversine distance between ({0},{1}) and ({2},{3}) should be approx {4}m")
    @CsvSource({
            "0.0, 0.0, 0.0, 0.0, 0.0",
            "18.5204, 73.8567, 18.5204, 73.8567, 0.0",
            "18.520430, 73.856743, 18.521330, 73.856743, 100.0"
    })
    @DisplayName("Haversine calculation accuracy verification")
    void testHaversineAccuracy(double lat1, double lon1, double lat2, double lon2, double expectedApprox) {
        double dist = validator.calculateHaversineDistance(lat1, lon1, lat2, lon2);
        if (expectedApprox == 0.0) {
            assertEquals(0.0, dist, 0.001);
        } else {
            assertEquals(expectedApprox, dist, 15.0); // Within 15m approximation
        }
    }
}
