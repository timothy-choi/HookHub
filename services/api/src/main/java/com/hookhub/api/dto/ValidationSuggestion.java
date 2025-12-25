package com.hookhub.api.dto;

public class ValidationSuggestion {
    private String field;
    private String issue;
    private String suggestion;

    public ValidationSuggestion() {
    }

    public ValidationSuggestion(String field, String issue, String suggestion) {
        this.field = field;
        this.issue = issue;
        this.suggestion = suggestion;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}

