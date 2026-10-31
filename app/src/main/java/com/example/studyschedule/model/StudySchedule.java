package com.example.studyschedule.model;

public class StudySchedule {
    private long id;
    private long studentId;
    private long subjectId;
    private String studyDate;
    private String startTime;
    private String endTime;
    private String location;
    private String status;

    public StudySchedule() {
    }

    public StudySchedule(long id, long studentId, long subjectId, String studyDate, String startTime, String endTime, String location, String status) {
        this.id = id;
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.studyDate = studyDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(long subjectId) {
        this.subjectId = subjectId;
    }

    public String getStudyDate() {
        return studyDate;
    }

    public void setStudyDate(String studyDate) {
        this.studyDate = studyDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
