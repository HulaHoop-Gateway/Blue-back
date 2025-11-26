package com.hulahoop.blueback.ai.model.dto;

import java.util.List;
import java.util.Map;

public class AiResponseDTO {
    private String message; // 일반 응답 메시지
    private List<Map<String, Object>> bicycles; // 자전거 목록
    private List<Map<String, Object>> cinemas; // 영화관 목록

    public AiResponseDTO() {
    }

    public AiResponseDTO(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Map<String, Object>> getBicycles() {
        return bicycles;
    }

    public void setBicycles(List<Map<String, Object>> bicycles) {
        this.bicycles = bicycles;
    }

    public List<Map<String, Object>> getCinemas() {
        return cinemas;
    }

    public void setCinemas(List<Map<String, Object>> cinemas) {
        this.cinemas = cinemas;
    }
}
