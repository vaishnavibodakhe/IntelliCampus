package com.intellicampus.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForgotPasswordResponseDto {

    private String message;
    private String demoResetCode;

    public ForgotPasswordResponseDto() {}

    public ForgotPasswordResponseDto(String message) {
        this.message = message;
    }

    public ForgotPasswordResponseDto(String message, String demoResetCode) {
        this.message = message;
        this.demoResetCode = demoResetCode;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDemoResetCode() { return demoResetCode; }
    public void setDemoResetCode(String demoResetCode) { this.demoResetCode = demoResetCode; }
}
