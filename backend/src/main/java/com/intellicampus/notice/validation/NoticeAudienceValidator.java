package com.intellicampus.notice.validation;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates notice audience targeting scope against approved institutional taxonomy.
 * Compliant with IC-SRS-1.0 Section 8.5 (NTC-01) and Test Case TC-10.
 */
@Component
public class NoticeAudienceValidator {

    public static final Set<String> ALLOWED_DEPARTMENTS = Set.of(
            "ALL",
            "ALL DEPARTMENTS",
            "COMPUTER SCIENCE",
            "INFORMATION TECHNOLOGY",
            "MECHANICAL ENGINEERING",
            "CIVIL ENGINEERING",
            "ELECTRICAL ENGINEERING"
    );

    public static final Set<String> ALLOWED_YEARS = Set.of(
            "ALL",
            "ALL YEARS",
            "YEAR 1",
            "YEAR 2",
            "YEAR 3",
            "YEAR 4",
            "FE",
            "SE",
            "TE",
            "BE"
    );

    public static final Set<String> ALLOWED_DIVISIONS = Set.of(
            "ALL",
            "ALL DIVISIONS",
            "DIV A",
            "DIV B",
            "DIV C",
            "DIVISION A",
            "DIVISION B",
            "DIVISION C"
    );

    /**
     * Validates that notice target parameters adhere to active institutional taxonomy.
     *
     * @param targetDepartment Target academic department
     * @param targetYear       Target academic cohort/year
     * @param targetDivision   Target academic division
     * @throws IllegalArgumentException If any scope parameter violates approved taxonomy
     */
    public void validateAudience(String targetDepartment, String targetYear, String targetDivision) {
        // Criterion 3: Notice audience validator verifies target department and year match active institutional taxonomy
        if (targetDepartment != null && !targetDepartment.trim().isEmpty()) {
            String normDept = targetDepartment.trim().toUpperCase();
            if (!ALLOWED_DEPARTMENTS.contains(normDept)) {
                throw new IllegalArgumentException(
                        String.format("Invalid target department '%s'. Must match active institutional taxonomy: %s",
                                targetDepartment, ALLOWED_DEPARTMENTS)
                );
            }
        }

        if (targetYear != null && !targetYear.trim().isEmpty()) {
            String normYear = targetYear.trim().toUpperCase();
            if (!ALLOWED_YEARS.contains(normYear)) {
                throw new IllegalArgumentException(
                        String.format("Invalid target year '%s'. Must match active institutional taxonomy: %s",
                                targetYear, ALLOWED_YEARS)
                );
            }
        }

        if (targetDivision != null && !targetDivision.trim().isEmpty()) {
            String normDiv = targetDivision.trim().toUpperCase();
            if (!ALLOWED_DIVISIONS.contains(normDiv)) {
                throw new IllegalArgumentException(
                        String.format("Invalid target division '%s'. Must match active institutional taxonomy: %s",
                                targetDivision, ALLOWED_DIVISIONS)
                );
            }
        }
    }
}
