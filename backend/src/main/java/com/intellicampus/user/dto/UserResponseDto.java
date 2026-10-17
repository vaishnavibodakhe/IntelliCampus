package com.intellicampus.user.dto;

import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.UserStatus;

public class UserResponseDto {
    private Long id;
    private String userCode;
    private String name;
    private String email;
    private Role role;
    private String department;
    private UserStatus status;
    private String rollNo;
    private String division;
    private Double attendanceRate;
    private String feeStatus;
    private Boolean faceEnrolled;
    private String phone;

    public UserResponseDto() {}

    public UserResponseDto(Long id, String userCode, String name, String email, Role role, String department, UserStatus status) {
        this.id = id;
        this.userCode = userCode;
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public Double getAttendanceRate() { return attendanceRate; }
    public void setAttendanceRate(Double attendanceRate) { this.attendanceRate = attendanceRate; }

    public String getFeeStatus() { return feeStatus; }
    public void setFeeStatus(String feeStatus) { this.feeStatus = feeStatus; }

    public Boolean getFaceEnrolled() { return faceEnrolled; }
    public void setFaceEnrolled(Boolean faceEnrolled) { this.faceEnrolled = faceEnrolled; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
