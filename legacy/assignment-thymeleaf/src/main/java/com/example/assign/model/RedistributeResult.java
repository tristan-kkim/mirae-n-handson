package com.example.assign.model;

import java.util.ArrayList;
import java.util.List;

public class RedistributeResult {

    private long distributionId;
    private boolean ok;
    private String code;
    private String message;
    private String summary;
    private int keptSubmissions;
    private int reopenTargets;
    private List<String> warnings = new ArrayList<>();
    private List<String> notes = new ArrayList<>();

    public long getDistributionId() {
        return distributionId;
    }

    public void setDistributionId(long distributionId) {
        this.distributionId = distributionId;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getKeptSubmissions() {
        return keptSubmissions;
    }

    public void setKeptSubmissions(int keptSubmissions) {
        this.keptSubmissions = keptSubmissions;
    }

    public int getReopenTargets() {
        return reopenTargets;
    }

    public void setReopenTargets(int reopenTargets) {
        this.reopenTargets = reopenTargets;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }
}
