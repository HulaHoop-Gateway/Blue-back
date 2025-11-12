package com.hulahoop.blueback.member.model.dto;

public class MemberDTO {

    private String memberCode;
    private String name;
    private String id;
    private String password;
    private String phoneNum;
    private String email;
    private String address;
    private String userType;
    private String notificationStatus;
    private String memberYn; // ✅ 회원 여부 (Y/N)

    // ===== Getter / Setter =====

    public String getMemberCode() {
        return memberCode;
    }

    public void setMemberCode(String memberCode) {
        this.memberCode = memberCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getNotificationStatus() {
        return notificationStatus;
    }

    public void setNotificationStatus(String notificationStatus) {
        this.notificationStatus = notificationStatus;
    }

    public String getMemberYn() {
        return memberYn;
    }

    public void setMemberYn(String memberYn) {
        this.memberYn = memberYn;
    }
}
