package com.hulahoop.blueback.member.model.dto;

public class MemberDTO {

    private String memberCode;          // 회원 코드 (PK)
    private String name;                // 이름
    private String id;                  // 로그인용 ID
    private String password;            // 비밀번호
    private String phoneNum;            // 전화번호
    private String email;               // 이메일
    private String address;             // 주소
    private String userType;            // 회원 유형 (U: 일반, A: 관리자)
    private String notificationStatus;  // 알림 수신 여부 (Y/N)

    // 기본 생성자
    public MemberDTO() {}

    // 전체 필드 생성자
    public MemberDTO(String memberCode, String name, String id, String password,
                     String phoneNum, String email, String address,
                     String userType, String notificationStatus) {
        this.memberCode = memberCode;
        this.name = name;
        this.id = id;
        this.password = password;
        this.phoneNum = phoneNum;
        this.email = email;
        this.address = address;
        this.userType = userType;
        this.notificationStatus = notificationStatus;
    }

    // Getter / Setter
    public String getMemberCode() { return memberCode; }
    public void setMemberCode(String memberCode) { this.memberCode = memberCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    @Override
    public String toString() {
        return "MemberDTO{" +
                "memberCode='" + memberCode + '\'' +
                ", name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", phoneNum='" + phoneNum + '\'' +
                ", email='" + email + '\'' +
                ", address='" + address + '\'' +
                ", userType='" + userType + '\'' +
                ", notificationStatus='" + notificationStatus + '\'' +
                '}';
    }
}
