package com.example.studyschedule.model;

public class Student {
    private long id;
    private String studentCode;
    private String fullname;
    private String email;
    private String phone;
    private long accountId;

    public Student() {
    }

    public Student(long id, String studentCode, String fullname, String email, String phone, long accountId) {
        this.id = id;
        this.studentCode = studentCode;
        this.fullname = fullname;
        this.email = email;
        this.phone = phone;
        this.accountId = accountId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public void setStudentCode(String studentCode) {
        this.studentCode = studentCode;
    }

    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }
}
