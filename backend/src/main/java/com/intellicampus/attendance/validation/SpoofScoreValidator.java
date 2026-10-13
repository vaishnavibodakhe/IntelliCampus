package com.intellicampus.attendance.validation;

import com.intellicampus.attendance.model.AttendanceStatus;
import org.springframework.stereotype.Component;

/**
 * Validates AI multi-modal biometric face recognition anti-spoofing confidence scores.
 * Flags records as REVIEW if confidence score is below threshold (0.60).
 * Compliant with IC-SRS-1.0 Section 8.1 and Test Case TC-04.
 */
@Component
public class SpoofScoreValidator {

    public static final double DEFAULT_SPOOF_THRESHOLD = 0.60;
    public static final double DEFAULT_FACE_MATCH_THRESHOLD = 0.50;

    private final double spoofThreshold;
    private final double faceMatchThreshold;

    public SpoofScoreValidator() {
        this.spoofThreshold = DEFAULT_SPOOF_THRESHOLD;
        this.faceMatchThreshold = DEFAULT_FACE_MATCH_THRESHOLD;
    }

    public SpoofScoreValidator(double spoofThreshold, double faceMatchThreshold) {
        this.spoofThreshold = spoofThreshold;
        this.faceMatchThreshold = faceMatchThreshold;
    }

    /**
     * Checks if anti-spoofing liveness confidence meets the required threshold.
     *
     * @param spoofScore Liveness anti-spoofing confidence score (0.0 to 1.0)
     * @return true if score >= 0.60
     */
    public boolean isPassing(Double spoofScore) {
        return spoofScore != null && spoofScore >= spoofThreshold;
    }

    /**
     * Evaluates attendance record status based on anti-spoofing score and facial matching confidence.
     *
     * @param spoofScore Anti-spoofing liveness score
     * @param faceScore  Facial recognition embedding cosine similarity
     * @param confirmed  Whether student confirmed their session check-in
     * @return AttendanceStatus (PRESENT, MATCHED, or REVIEW)
     */
    public AttendanceStatus evaluateStatus(Double spoofScore, Double faceScore, boolean confirmed) {
        // Criterion 3: Flag records as REVIEW if anti-spoofing confidence is below threshold (0.60)
        if (spoofScore != null && spoofScore < spoofThreshold) {
            return AttendanceStatus.REVIEW;
        }

        if (faceScore != null && faceScore < faceMatchThreshold) {
            return AttendanceStatus.REVIEW;
        }

        if (confirmed) {
            return AttendanceStatus.PRESENT;
        }

        return AttendanceStatus.MATCHED;
    }

    /**
     * Constructs user-friendly explanatory reason when a status is assigned.
     */
    public String resolveStatusReason(Double spoofScore, Double faceScore, Double distanceMeters) {
        if (spoofScore != null && spoofScore < spoofThreshold) {
            return String.format("Anti-spoofing alert: Confidence score (%.2f) below safety threshold (%.2f). Flagged for faculty manual review.",
                    spoofScore, spoofThreshold);
        }

        if (faceScore != null && faceScore < faceMatchThreshold) {
            return String.format("Face match warning: Cosine similarity (%.2f) below threshold (%.2f). Flagged for faculty manual review.",
                    faceScore, faceMatchThreshold);
        }

        if (distanceMeters != null) {
            return String.format("Verified via GPS Geofence (%.0fm) and Dynamic QR Token.", distanceMeters);
        }

        return "Attendance confirmed and biometric identity verified.";
    }

    public double getSpoofThreshold() {
        return spoofThreshold;
    }

    public double getFaceMatchThreshold() {
        return faceMatchThreshold;
    }
}
