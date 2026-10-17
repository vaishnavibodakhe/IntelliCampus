package com.intellicampus.user.dto;

public class StudentRegisterRequestDto {
    private String fullName;
    private String email;
    private String password;
    private String confirmPassword;
    private String contactNo;
    private String rollNo;
    private String department;
    private String year;
    private String division;
    private Boolean biometricConsent;
    private String consentVersion;

    public StudentRegisterRequestDto() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getContactNo() { return contactNo; }
    public void setContactNo(String contactNo) { this.contactNo = contactNo; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public Boolean getBiometricConsent() { return biometricConsent; }
    public void setBiometricConsent(Boolean biometricConsent) { this.biometricConsent = biometricConsent; }

    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }
}
