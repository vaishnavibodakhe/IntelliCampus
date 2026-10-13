package com.intellicampus.attendance.validation;

import org.springframework.stereotype.Component;

/**
 * Validates GPS location boundaries for dynamic classroom attendance.
 * Implements the Haversine Great Circle distance formula and device accuracy constraints.
 * Compliant with IC-SRS-1.0 Section 8.2 and Test Case TC-02.
 */
@Component
public class GeofenceValidator {

    public static final double MAX_GPS_ACCURACY_METERS = 100.0;
    public static final int DEFAULT_GEOFENCE_RADIUS_METERS = 50;
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Validates that the student's reported coordinates fall inside the designated classroom geofence.
     *
     * @param studentLat       Student device latitude
     * @param studentLng       Student device longitude
     * @param accuracyMeters   Student device reported GPS accuracy (null-safe)
     * @param roomLat          Classroom central latitude
     * @param roomLng          Classroom central longitude
     * @param allowableRadiusM Classroom maximum allowable geofence radius in meters (null-safe, defaults to 50)
     * @return Calculated distance in meters
     * @throws GeofenceValidationException If coordinates are invalid, accuracy > 100m, or distance > allowableRadius
     */
    public double validate(Double studentLat, Double studentLng, Double accuracyMeters,
                           Double roomLat, Double roomLng, Integer allowableRadiusM) {

        if (studentLat == null || studentLng == null) {
            throw new GeofenceValidationException("Student latitude and longitude are mandatory for geofence confirmation.");
        }
        if (roomLat == null || roomLng == null) {
            throw new GeofenceValidationException("Classroom geofence center coordinates are not configured.");
        }

        // Criterion 2: Reject check-ins where GPS accuracy is greater than 100 meters
        if (accuracyMeters != null && accuracyMeters > MAX_GPS_ACCURACY_METERS) {
            throw new GeofenceValidationException(
                    String.format("GPS accuracy (±%.1fm) exceeds maximum allowable threshold of %.0fm. Please calibrate device location or move near a window.",
                            accuracyMeters, MAX_GPS_ACCURACY_METERS),
                    accuracyMeters
            );
        }

        int maxRadius = (allowableRadiusM != null && allowableRadiusM > 0)
                ? allowableRadiusM
                : DEFAULT_GEOFENCE_RADIUS_METERS;

        double distance = calculateHaversineDistance(studentLat, studentLng, roomLat, roomLng);

        // Criterion 1: Haversine formula validates student GPS lat/lon is within room radius (default 50m)
        if (distance > maxRadius) {
            throw new GeofenceValidationException(
                    String.format("Geofence alert: Location is %dm away, exceeding classroom boundary of %dm.",
                            Math.round(distance), maxRadius),
                    distance,
                    maxRadius
            );
        }

        return distance;
    }

    /**
     * Calculates distance in meters between two latitude/longitude points using the Haversine formula.
     */
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
