package com.intellicampus.user.dto;

import java.util.ArrayList;
import java.util.List;

public class CsvImportResultDto {
    private int totalProcessed;
    private int importedCount;
    private int failedCount;
    private List<String> errors = new ArrayList<>();

    public CsvImportResultDto() {}

    public CsvImportResultDto(int totalProcessed, int importedCount, int failedCount, List<String> errors) {
        this.totalProcessed = totalProcessed;
        this.importedCount = importedCount;
        this.failedCount = failedCount;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public int getTotalProcessed() { return totalProcessed; }
    public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }

    public int getImportedCount() { return importedCount; }
    public void setImportedCount(int importedCount) { this.importedCount = importedCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
