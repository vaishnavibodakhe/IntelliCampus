package com.intellicampus.user.dto;

import com.intellicampus.user.model.Role;

import java.util.HashMap;
import java.util.Map;

public class AuthResponse {

    private boolean success = true;
    private String message = "Authentication successful";
    private String token;
    private String tokenType = "Bearer";
    private Long id;
    private String userCode;
    private String name;
    private String email;
    private Role role;
    private String department;
    private String phone;
    private String specialization;
    private Map<String, Object> data;

    public AuthResponse() {}

    public AuthResponse(String token, Long id, String userCode, String name, String email, Role role, String department, String phone) {
        this(token, id, userCode, name, email, role, department, phone, null);
    }

    public AuthResponse(String token, Long id, String userCode, String name, String email, Role role, String department, String phone, String specialization) {
        this.token = token;
        this.tokenType = "Bearer";
        this.id = id;
        this.userCode = userCode;
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
        this.phone = phone;
        this.specialization = specialization;
        this.success = true;
        this.message = "Authentication successful";
        rebuildDataMap();
    }

    private void rebuildDataMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("token", this.token);
        map.put("role", this.role != null ? this.role.name().toLowerCase() : "faculty");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", this.id);
        userMap.put("name", this.name);
        userMap.put("email", this.email);
        userMap.put("employeeId", this.userCode);
        userMap.put("userCode", this.userCode);
        userMap.put("role", this.role != null ? this.role.name() : "FACULTY");
        userMap.put("department", this.department);
        userMap.put("phone", this.phone);
        if (this.specialization != null) {
            userMap.put("specialization", this.specialization);
        }
        map.put("user", userMap);
        this.data = map;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getToken() { return token; }
    public void setToken(String token) {
        this.token = token;
        rebuildDataMap();
    }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public Long getId() { return id; }
    public void setId(Long id) {
        this.id = id;
        rebuildDataMap();
    }

    public String getUserCode() { return userCode; }
    public void setUserCode(String userCode) {
        this.userCode = userCode;
        rebuildDataMap();
    }

    public String getEmployeeId() { return userCode; }
    public void setEmployeeId(String employeeId) {
        this.userCode = employeeId;
        rebuildDataMap();
    }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        rebuildDataMap();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email;
        rebuildDataMap();
    }

    public Role getRole() { return role; }
    public void setRole(Role role) {
        this.role = role;
        rebuildDataMap();
    }

    public String getDepartment() { return department; }
    public void setDepartment(String department) {
        this.department = department;
        rebuildDataMap();
    }

    public String getPhone() { return phone; }
    public void setPhone(String phone) {
        this.phone = phone;
        rebuildDataMap();
    }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) {
        this.specialization = specialization;
        rebuildDataMap();
    }

    public Map<String, Object> getData() {
        if (this.data == null) {
            rebuildDataMap();
        }
        return this.data;
    }

    public void setData(Map<String, Object> data) { this.data = data; }
}
