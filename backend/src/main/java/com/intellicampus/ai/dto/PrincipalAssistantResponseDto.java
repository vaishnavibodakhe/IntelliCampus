package com.intellicampus.ai.dto;

import java.util.ArrayList;
import java.util.List;

public class PrincipalAssistantResponseDto {

    private String answer;
    private List<String> dataSources = new ArrayList<>();
    private boolean refusal;

    public PrincipalAssistantResponseDto() {}

    public PrincipalAssistantResponseDto(String answer, List<String> dataSources, boolean refusal) {
        this.answer = answer;
        this.dataSources = dataSources != null ? dataSources : new ArrayList<>();
        this.refusal = refusal;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getDataSources() {
        return dataSources;
    }

    public void setDataSources(List<String> dataSources) {
        this.dataSources = dataSources != null ? dataSources : new ArrayList<>();
    }

    public boolean isRefusal() {
        return refusal;
    }

    public void setRefusal(boolean refusal) {
        this.refusal = refusal;
    }
}
