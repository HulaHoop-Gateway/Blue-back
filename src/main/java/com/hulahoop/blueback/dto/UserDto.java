package com.hulahoop.blueback.dto;

public class UserDto {

    private String memberCode;  // member_code 필드 추가
    private String username;
    private String name;        // name 필드 추가
    private String email;

    // =======================
    // 기본 생성자
    // =======================
    public UserDto() {}

    // =======================
    // 전체 필드 생성자
    // =======================
    public UserDto(String memberCode, String username, String name, String email) {
        this.memberCode = memberCode;
        this.username = username;
        this.name = name;
        this.email = email;
    }

    // =======================
    // Getter / Setter
    // =======================
    public String getMemberCode() {
        return memberCode;
    }

    public void setMemberCode(String memberCode) {
        this.memberCode = memberCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
