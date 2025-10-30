package com.hulahoop.blueback.auth.model.dto;

public class LoginRequest {

    private String id;
    private String password;

    // ✅ 기본 생성자
    public LoginRequest() {}

    // ✅ 전체 생성자
    public LoginRequest(String id, String password) {
        this.id = id;
        this.password = password;
    }

    // ✅ getter / setter
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
}
