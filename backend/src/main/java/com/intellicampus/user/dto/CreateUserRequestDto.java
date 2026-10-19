package com.intellicampus.user.dto;

import com.intellicampus.user.model.Role;

public class CreateUserRequestDto {
    private String userCode;
    private String name;
    private String email;
    private Role role;
    private String department;
    private String rollNo;
    private String division;
    private String year;
    private String rawPassword;

    public CreateUserRequestDto() {}

    public String getUserCode() { return userCode; }
    public void setUserCode(String userCode) { this.userCode = userCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getRawPassword() { return rawPassword; }
    public void setRawPassword(String rawPassword) { this.rawPassword = rawPassword; }

    private Boolean biometricConsent;
    private String consentVersion;
    private String facePhotoBase64;

    public Boolean getBiometricConsent() { return biometricConsent; }
    public void setBiometricConsent(Boolean biometricConsent) { this.biometricConsent = biometricConsent; }

    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }

    public String getFacePhotoBase64() { return facePhotoBase64; }
    public void setFacePhotoBase64(String facePhotoBase64) { this.facePhotoBase64 = facePhotoBase64; }
}
