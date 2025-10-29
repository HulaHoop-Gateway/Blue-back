package com.hulahoop.blueback.dto;

// 📌 JPA 어노테이션 전부 제거
public class MemberDTO {

    private String memberCode;
    private String name;
    private String username;  // 로그인용 id
    private String password;
    private String phoneNum;
    private String email;
    private String address;
    private String userType;
    private String notificationStatus;

    public MemberDTO() {}

    public MemberDTO(String memberCode, String name, String username, String password,
                  String phoneNum, String email, String address, String userType,
                  String notificationStatus) {
        this.memberCode = memberCode;
        this.name = name;
        this.username = username;
        this.password = password;
        this.phoneNum = phoneNum;
        this.email = email;
        this.address = address;
        this.userType = userType;
        this.notificationStatus = notificationStatus;
    }

    public String getMemberCode() { return memberCode; }
    public void setMemberCode(String memberCode) { this.memberCode = memberCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhoneNum() { return phoneNum; }
    public void setPhoneNum(String phoneNum) { this.phoneNum = phoneNum; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getNotificationStatus() { return notificationStatus; }
    public void setNotificationStatus(String notificationStatus) { this.notificationStatus = notificationStatus; }
}
