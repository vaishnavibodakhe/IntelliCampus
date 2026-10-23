package com.intellicampus.user.validation;

import com.intellicampus.user.dto.CreateUserRequestDto;
import com.intellicampus.user.model.Role;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.*;

/**
 * Validates bulk CSV user uploads, enforcing mandatory headers, strong password policies,
 * role gating, and duplicate record detection.
 */
@Component
public class CsvUserValidator {

    public static final List<String> LEGACY_REQUIRED_HEADERS = List.of(
            "usercode", "name", "email", "department", "rollno"
    );

    public static class CsvValidationResult {
        private final List<CreateUserRequestDto> validRows;
        private final List<String> errors;

        public CsvValidationResult(List<CreateUserRequestDto> validRows, List<String> errors) {
            this.validRows = validRows;
            this.errors = errors;
        }

        public List<CreateUserRequestDto> getValidRows() { return validRows; }
        public List<String> getErrors() { return errors; }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    /**
     * Parses and validates raw CSV lines.
     *
     * @param csvLines List of raw lines from the uploaded CSV
     * @return CsvValidationResult with validated rows and detailed line-number error messages
     */
    public CsvValidationResult validateCsvLines(List<String> csvLines) {
        List<CreateUserRequestDto> validRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (csvLines == null || csvLines.isEmpty()) {
            errors.add("Uploaded CSV file is empty.");
            return new CsvValidationResult(validRows, errors);
        }

        int headerIndex = -1;
        Map<String, Integer> headerMap = new HashMap<>();

        // Locate and validate required headers
        for (int i = 0; i < csvLines.size(); i++) {
            String line = csvLines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] rawHeaders = line.split(",");
            for (int col = 0; col < rawHeaders.length; col++) {
                String cleanHeader = rawHeaders[col].trim().toLowerCase().replaceAll("^[\"']|[\"']$", "");
                headerMap.put(cleanHeader, col);
            }

            headerIndex = i;
            break;
        }

        if (headerIndex == -1) {
            errors.add("No valid header row found in CSV.");
            return new CsvValidationResult(validRows, errors);
        }

        // Determine format: Staff format vs Legacy student format
        boolean hasFullName = headerMap.containsKey("full_name") || headerMap.containsKey("fullname");
        boolean hasName = headerMap.containsKey("name");
        boolean hasPassword = headerMap.containsKey("password");
        boolean hasRollNo = headerMap.containsKey("rollno") || headerMap.containsKey("roll_no");

        boolean isStaffFormat = hasPassword || hasFullName || !hasRollNo;

        if (isStaffFormat) {
            if (!hasFullName && !hasName) {
                errors.add("Missing required CSV header: full_name (or name). Expected headers: full_name, email, password, role, department.");
                return new CsvValidationResult(validRows, errors);
            }
            if (!headerMap.containsKey("email")) {
                errors.add("Missing required CSV header: email. Expected headers: full_name, email, password, role, department.");
                return new CsvValidationResult(validRows, errors);
            }
            if (!headerMap.containsKey("department")) {
                errors.add("Missing required CSV header: department. Expected headers: full_name, email, password, role, department.");
                return new CsvValidationResult(validRows, errors);
            }
            if (!headerMap.containsKey("password")) {
                errors.add("Missing required CSV header: password. Expected headers: full_name, email, password, role, department.");
                return new CsvValidationResult(validRows, errors);
            }
            if (!headerMap.containsKey("role")) {
                errors.add("Missing required CSV header: role. Expected headers: full_name, email, password, role, department.");
                return new CsvValidationResult(validRows, errors);
            }
        } else {
            // Legacy format check
            List<String> missingHeaders = new ArrayList<>();
            for (String req : LEGACY_REQUIRED_HEADERS) {
                if (!headerMap.containsKey(req)) {
                    missingHeaders.add(req);
                }
            }
            if (!missingHeaders.isEmpty()) {
                errors.add("Missing required CSV header: " + String.join(", ", missingHeaders) +
                        ". Expected headers: userCode, name, email, department, rollNo, [role, division] or full_name, email, password, role, department.");
                return new CsvValidationResult(validRows, errors);
            }
        }

        Map<String, Integer> seenUserCodes = new HashMap<>();
        Map<String, Integer> seenEmails = new HashMap<>();
        int autoSeq = 1;
        int currentYear = Year.now().getValue();

        for (int i = headerIndex + 1; i < csvLines.size(); i++) {
            int lineNum = i + 1;
            String line = csvLines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] cols = line.split(",");

            // Extract Name
            Integer nameCol = headerMap.get("full_name");
            if (nameCol == null) nameCol = headerMap.get("fullname");
            if (nameCol == null) nameCol = headerMap.get("name");

            Integer emailCol = headerMap.get("email");
            Integer deptCol = headerMap.get("department");
            Integer roleCol = headerMap.get("role");
            Integer pwCol = headerMap.get("password");
            Integer userCodeCol = headerMap.get("usercode");
            if (userCodeCol == null) userCodeCol = headerMap.get("user_code");
            Integer rollNoCol = headerMap.get("rollno");
            if (rollNoCol == null) rollNoCol = headerMap.get("roll_no");

            if (nameCol == null || emailCol == null || deptCol == null) {
                errors.add(String.format("Line %d: Insufficient columns for required fields.", lineNum));
                continue;
            }

            int maxIndex = Math.max(nameCol, Math.max(emailCol, deptCol));
            if (roleCol != null) maxIndex = Math.max(maxIndex, roleCol);
            if (pwCol != null) maxIndex = Math.max(maxIndex, pwCol);

            if (cols.length <= maxIndex) {
                errors.add(String.format("Line %d: Insufficient columns for required fields.", lineNum));
                continue;
            }

            String name = clean(cols[nameCol]);
            String email = clean(cols[emailCol]).toLowerCase();
            String department = clean(cols[deptCol]);

            if (name.isEmpty() || email.isEmpty() || department.isEmpty()) {
                errors.add(String.format("Line %d: Empty values detected in required fields (name, email, or department).", lineNum));
                continue;
            }

            if (!email.contains("@") || !email.contains(".")) {
                errors.add(String.format("Line %d: Invalid email format for '%s'.", lineNum, email));
                continue;
            }

            // Check duplicate email
            if (seenEmails.containsKey(email)) {
                int prevLine = seenEmails.get(email);
                errors.add(String.format("Line %d: Duplicate email '%s' (first appeared on Line %d).", lineNum, email, prevLine));
                continue;
            } else {
                seenEmails.put(email, lineNum);
            }

            // Extract & Validate Role
            String rStr = roleCol != null && cols.length > roleCol ? clean(cols[roleCol]).toUpperCase() : "";
            Role role;
            if (rStr.isEmpty()) {
                role = isStaffFormat ? null : Role.STUDENT;
            } else {
                try {
                    role = Role.valueOf(rStr);
                } catch (IllegalArgumentException ex) {
                    errors.add(String.format("Line %d: Invalid role '%s'. Only HOD, FACULTY, and MENTOR can be imported.", lineNum, rStr));
                    continue;
                }
            }

            // Role Rejections
            if (isStaffFormat) {
                if (role == Role.STUDENT || "STUDENT".equalsIgnoreCase(rStr)) {
                    errors.add(String.format("Line %d: Students register themselves on the Student portal.", lineNum));
                    continue;
                }
                if (role == Role.PRINCIPAL || "ADMIN".equalsIgnoreCase(rStr) || "PRINCIPAL".equalsIgnoreCase(rStr)) {
                    errors.add(String.format("Line %d: Principal cannot be imported. Creating PRINCIPAL role via CSV import is strictly prohibited.", lineNum));
                    continue;
                }
                if (role != Role.HOD && role != Role.FACULTY && role != Role.MENTOR) {
                    errors.add(String.format("Line %d: Invalid role '%s'. Only HOD, FACULTY, and MENTOR can be imported.", lineNum, rStr));
                    continue;
                }
            } else {
                if (role == Role.PRINCIPAL || "ADMIN".equalsIgnoreCase(rStr) || "PRINCIPAL".equalsIgnoreCase(rStr)) {
                    errors.add(String.format("Line %d: Principal cannot be imported. Creating PRINCIPAL role via CSV import is strictly prohibited.", lineNum));
                    continue;
                }
            }

            // Extract & Validate Password
            String rawPassword = pwCol != null && cols.length > pwCol ? clean(cols[pwCol]) : "";
            if (isStaffFormat) {
                if (rawPassword.isEmpty()) {
                    errors.add(String.format("Line %d: Password is required for staff accounts.", lineNum));
                    continue;
                }
                if (rawPassword.length() < 8 ||
                        !rawPassword.matches(".*[A-Z].*") ||
                        !rawPassword.matches(".*[a-z].*") ||
                        !rawPassword.matches(".*[0-9].*") ||
                        !rawPassword.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
                    errors.add(String.format("Line %d: Password for '%s' does not meet complexity requirements (min 8 chars, uppercase, lowercase, number, special char).", lineNum, email));
                    continue;
                }
            }

            // Extract or Auto-Generate User Code
            String userCode = userCodeCol != null && cols.length > userCodeCol ? clean(cols[userCodeCol]) : "";
            if (userCode.isEmpty()) {
                String deptAbbr = department.length() <= 4 ? department.toUpperCase().replaceAll("\\s+", "") : department.substring(0, 3).toUpperCase();
                userCode = String.format("%s-%d-%03d", deptAbbr, currentYear, autoSeq++);
            }

            // Check duplicate userCode
            if (seenUserCodes.containsKey(userCode.toLowerCase())) {
                int prevLine = seenUserCodes.get(userCode.toLowerCase());
                errors.add(String.format("Line %d: Duplicate userCode '%s' (first appeared on Line %d).", lineNum, userCode, prevLine));
                continue;
            } else {
                seenUserCodes.put(userCode.toLowerCase(), lineNum);
            }

            String rollNo = rollNoCol != null && cols.length > rollNoCol ? clean(cols[rollNoCol]) : "";

            CreateUserRequestDto dto = new CreateUserRequestDto();
            dto.setUserCode(userCode);
            dto.setName(name);
            dto.setEmail(email);
            dto.setRole(role);
            dto.setDepartment(department);
            dto.setRawPassword(rawPassword.isEmpty() ? null : rawPassword);
            dto.setRollNo(rollNo.isEmpty() ? null : rollNo);
            validRows.add(dto);
        }

        return new CsvValidationResult(validRows, errors);
    }

    private String clean(String val) {
        if (val == null) return "";
        return val.trim().replaceAll("^[\"']|[\"']$", "");
    }
}
