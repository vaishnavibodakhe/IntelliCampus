package com.intellicampus.attendance.validation;

/**
 * Thrown when student GPS coordinates or device accuracy violate classroom geofence perimeters.
 * Compliant with IC-SRS-1.0 Section 8.2 and Test Case TC-02.
 */
public class GeofenceValidationException extends RuntimeException {

    private final Double distanceMeters;
    private final Integer allowableRadiusMeters;
    private final Double accuracyMeters;

    public GeofenceValidationException(String message) {
        super(message);
        this.distanceMeters = null;
        this.allowableRadiusMeters = null;
        this.accuracyMeters = null;
    }

    public GeofenceValidationException(String message, Double distanceMeters, Integer allowableRadiusMeters) {
        super(message);
        this.distanceMeters = distanceMeters;
        this.allowableRadiusMeters = allowableRadiusMeters;
        this.accuracyMeters = null;
    }

    public GeofenceValidationException(String message, Double accuracyMeters) {
        super(message);
        this.distanceMeters = null;
        this.allowableRadiusMeters = null;
        this.accuracyMeters = accuracyMeters;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public Integer getAllowableRadiusMeters() {
        return allowableRadiusMeters;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }
}
