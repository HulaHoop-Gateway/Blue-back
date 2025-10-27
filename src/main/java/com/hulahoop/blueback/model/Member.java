package com.hulahoop.blueback.model;

import jakarta.persistence.*;

@Entity
@Table(name = "T_Member")
public class Member {

    @Id
    @Column(name = "member_code", length = 10)
    private String memberCode;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "id", length = 15, nullable = false, unique = true)
    private String username; // 로그인용 id

    @Column(name = "password", length = 100, nullable = false)
    private String password;

    @Column(name = "phone_num", length = 15, nullable = false)
    private String phoneNum;

    @Column(name = "email", length = 50)
    private String email;

    @Column(name = "address", length = 200, nullable = false)
    private String address;

    @Column(name = "user_type", length = 1, nullable = false)
    private String userType;

    @Column(name = "notifiation_status", length = 1)
    private String notificationStatus;

    public Member() {}

    // 생성자, getter, setter
    public Member(String memberCode, String name, String username, String password,
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
