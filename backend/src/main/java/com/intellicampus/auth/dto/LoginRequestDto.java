package com.intellicampus.auth.dto;

public class LoginRequestDto {

    private String userCode;
    private String loginCode;
    private String password;

    public LoginRequestDto() {}

    public LoginRequestDto(String userCode, String password) {
        this.userCode = userCode;
        this.loginCode = userCode;
        this.password = password;
    }

    public String getUserCode() {
        if (userCode != null && !userCode.isBlank()) return userCode;
        return loginCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
        if (this.loginCode == null) this.loginCode = userCode;
    }

    public String getLoginCode() {
        if (loginCode != null && !loginCode.isBlank()) return loginCode;
        return userCode;
    }

    public void setLoginCode(String loginCode) {
        this.loginCode = loginCode;
        if (this.userCode == null) this.userCode = loginCode;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
