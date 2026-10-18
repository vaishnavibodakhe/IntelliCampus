package com.intellicampus.auth.dto;

public class RegisterRequestDto {

    private String name;
    private String email;
    private String loginCode;
    private String userCode;
    private String rollNo;
    private String department;
    private String year;
    private String division;
    private String password;
    private String confirmPassword;
    private Boolean biometricConsent;
    private String consentVersion;

    public RegisterRequestDto() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getLoginCode() {
        if (loginCode != null && !loginCode.isBlank()) return loginCode;
        return userCode;
    }
    public void setLoginCode(String loginCode) {
        this.loginCode = loginCode;
        if (this.userCode == null) this.userCode = loginCode;
    }

    public String getUserCode() {
        if (userCode != null && !userCode.isBlank()) return userCode;
        return loginCode;
    }
    public void setUserCode(String userCode) {
        this.userCode = userCode;
        if (this.loginCode == null) this.loginCode = userCode;
    }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public Boolean getBiometricConsent() { return biometricConsent; }
    public void setBiometricConsent(Boolean biometricConsent) { this.biometricConsent = biometricConsent; }

    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }
}
