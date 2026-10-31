package com.example.studyschedule.model;

public class Subject {
    private long id;
    private String subjectCode;
    private String subjectName;
    private String description;
    private int credits;

    public Subject() {
    }

    public Subject(long id, String subjectCode, String subjectName, String description, int credits) {
        this.id = id;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.description = description;
        this.credits = credits;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }
}
