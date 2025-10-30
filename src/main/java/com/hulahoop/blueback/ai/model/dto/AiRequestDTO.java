package com.hulahoop.blueback.ai.model.dto;

public class AiRequestDTO {
    private String message; // 사용자가 입력한 자연어 요청

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
