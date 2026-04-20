package com.hulahoop.blueback.member.model.dto;

public class MemberDTO {

    // private 제어자: 다른 외부 객체나 클래스에서 몰래 내 변수값에 마음대로 접근하지 못하도록 철저히 막는 '캡슐화(데이터 보호)'의
    // 기본.
    // 오직 아래에 열어둔 합법적인 공개 통로(public Getter/Setter)를 통해서만 데이터를 안전하게 넣고 뺄 수 있게 통제함.
    private String memberCode;
    private String name;
    private String id;
    private String password;
    private String phoneNum;
    private String email;
    private String address;
    private String userType;
    private String notificationStatus;
    private String memberYn; // 탈퇴 여부 - Y면 유효한 회원, N이면 탈퇴 처리된 상태

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
