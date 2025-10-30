package com.hulahoop.blueback.ai.model.dto;

import java.util.List;
import java.util.Map;

public class GeminiRequestDTO {
    private List<Map<String, Object>> contents;

    public GeminiRequestDTO(String userMessage) {
        this.contents = List.of(
                Map.of("parts", List.of(Map.of("text", userMessage)))
        );
    }

    public List<Map<String, Object>> getContents() {
        return contents;
    }

    public void setContents(List<Map<String, Object>> contents) {
        this.contents = contents;
    }
}
