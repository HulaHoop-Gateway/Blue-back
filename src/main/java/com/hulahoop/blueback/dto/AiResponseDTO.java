package com.hulahoop.blueback.dto;

public class AiResponseDTO {
    private String result; // Gemini 응답 요약 결과

    public AiResponseDTO() {}

    public AiResponseDTO(String result) {
        this.result = result;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
