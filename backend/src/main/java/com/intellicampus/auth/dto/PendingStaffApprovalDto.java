package com.intellicampus.auth.dto;

import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.UserStatus;

import java.time.LocalDateTime;

public class PendingStaffApprovalDto {

    private Long id;
    private String userCode;
    private String name;
    private String email;
    private Role role;
    private String department;
    private UserStatus status;
    private LocalDateTime createdAt;

    public PendingStaffApprovalDto() {}

    public PendingStaffApprovalDto(Long id, String userCode, String name, String email, Role role, String department, UserStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.userCode = userCode;
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
        this.status = status;
        this.createdAt = createdAt;
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
