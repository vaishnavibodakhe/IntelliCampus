package com.intellicampus.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PrincipalAssistantQueryDto {

    @NotBlank(message = "Question must not be blank")
    @Size(max = 500, message = "Question must not exceed 500 characters")
    private String question;

    public PrincipalAssistantQueryDto() {}

    public PrincipalAssistantQueryDto(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
