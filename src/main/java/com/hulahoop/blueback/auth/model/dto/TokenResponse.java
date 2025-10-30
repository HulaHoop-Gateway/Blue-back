package com.hulahoop.blueback.auth.model.dto;

public class TokenResponse {

    private String token;

    // =======================
    // 기본 생성자
    // =======================
    public TokenResponse() {}

    // =======================
    // 전체 필드 생성자
    // =======================
    public TokenResponse(String token) {
        this.token = token;
    }

    // =======================
    // Getter / Setter
    // =======================
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
