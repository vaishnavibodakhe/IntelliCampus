package com.intellicampus.user.validation;

import com.intellicampus.user.dto.CreateUserRequestDto;
import com.intellicampus.user.model.Role;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates bulk CSV student uploads, enforcing mandatory headers and duplicate record detection.
 * Compliant with IC-SRS-1.0 Section 8.5 (ADM-01) and Test Case TC-09.
 */
@Component
public class CsvUserValidator {

    public static final List<String> REQUIRED_HEADERS = List.of(
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

        // Criterion 1: CSV parser validates required headers: userCode, name, email, department, rollNo
        List<String> missingHeaders = new ArrayList<>();
        for (String req : REQUIRED_HEADERS) {
            if (!headerMap.containsKey(req)) {
                missingHeaders.add(req);
            }
        }

        if (!missingHeaders.isEmpty()) {
            errors.add("Missing required CSV header: " + String.join(", ", missingHeaders) +
                    ". Expected headers: userCode, name, email, department, rollNo, [role, division].");
            return new CsvValidationResult(validRows, errors);
        }

        // Criterion 2: Duplicate email or userCode entries are rejected with specific line numbers in error report
        Map<String, Integer> seenUserCodes = new HashMap<>();
        Map<String, Integer> seenEmails = new HashMap<>();

        for (int i = headerIndex + 1; i < csvLines.size(); i++) {
            int lineNum = i + 1;
            String line = csvLines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] cols = line.split(",");
            int userCodeCol = headerMap.get("usercode");
            int nameCol = headerMap.get("name");
            int emailCol = headerMap.get("email");
            int deptCol = headerMap.get("department");
            int rollNoCol = headerMap.get("rollno");

            if (cols.length <= Math.max(userCodeCol, Math.max(nameCol, Math.max(emailCol, Math.max(deptCol, rollNoCol))))) {
                errors.add(String.format("Line %d: Insufficient columns for required fields.", lineNum));
                continue;
            }

            String userCode = clean(cols[userCodeCol]);
            String name = clean(cols[nameCol]);
            String email = clean(cols[emailCol]).toLowerCase();
            String department = clean(cols[deptCol]);
            String rollNo = clean(cols[rollNoCol]);

            if (userCode.isEmpty() || name.isEmpty() || email.isEmpty() || department.isEmpty()) {
                errors.add(String.format("Line %d: Empty values detected in required fields (userCode, name, email, or department).", lineNum));
                continue;
            }

            if (!email.contains("@") || !email.contains(".")) {
                errors.add(String.format("Line %d: Invalid email format for '%s'.", lineNum, email));
                continue;
            }

            // Check duplicate userCode
            if (seenUserCodes.containsKey(userCode.toLowerCase())) {
                int prevLine = seenUserCodes.get(userCode.toLowerCase());
                errors.add(String.format("Line %d: Duplicate userCode '%s' (first appeared on Line %d).", lineNum, userCode, prevLine));
                continue;
            } else {
                seenUserCodes.put(userCode.toLowerCase(), lineNum);
            }

            // Check duplicate email
            if (seenEmails.containsKey(email)) {
                int prevLine = seenEmails.get(email);
                errors.add(String.format("Line %d: Duplicate email '%s' (first appeared on Line %d).", lineNum, email, prevLine));
                continue;
            } else {
                seenEmails.put(email, lineNum);
            }

            // Optional role and division columns
            Role role = Role.STUDENT;
            if (headerMap.containsKey("role") && cols.length > headerMap.get("role")) {
                String rStr = clean(cols[headerMap.get("role")]).toUpperCase();
                try {
                    role = Role.valueOf(rStr);
                } catch (IllegalArgumentException ignored) {
                    role = Role.STUDENT;
                }
            }

            String division = "Div A";
            if (headerMap.containsKey("division") && cols.length > headerMap.get("division")) {
                String dStr = clean(cols[headerMap.get("division")]);
                if (!dStr.isEmpty()) division = dStr;
            }

            CreateUserRequestDto dto = new CreateUserRequestDto();
            dto.setUserCode(userCode);
            dto.setName(name);
            dto.setEmail(email);
            dto.setRole(role);
            dto.setDepartment(department);
            dto.setRollNo(rollNo);
            dto.setDivision(division);
            validRows.add(dto);
        }

        return new CsvValidationResult(validRows, errors);
    }

    private String clean(String val) {
        if (val == null) return "";
        return val.trim().replaceAll("^[\"']|[\"']$", "");
    }
}
