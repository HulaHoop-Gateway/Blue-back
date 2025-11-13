package com.hulahoop.blueback.ai.model.dto;

import java.util.List;

public class AiResponseDTO {
    private String message; // 일반 응답 메시지
    private List<BikeDTO> bicycles; // 자전거 목록

    public AiResponseDTO() {}

    public AiResponseDTO(String message) {
        this.message = message;
    }

    public AiResponseDTO(List<BikeDTO> bicycles) {
        this.bicycles = bicycles;
    }

    public AiResponseDTO(String message, List<BikeDTO> bicycles) {
        this.message = message;
        this.bicycles = bicycles;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<BikeDTO> getBicycles() {
        return bicycles;
    }

    public void setBicycles(List<BikeDTO> bicycles) {
        this.bicycles = bicycles;
    }
}
