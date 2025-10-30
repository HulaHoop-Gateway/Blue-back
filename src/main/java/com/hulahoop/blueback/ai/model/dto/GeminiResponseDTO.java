package com.hulahoop.blueback.ai.model.dto;

import java.util.List;
import java.util.Map;

public class GeminiResponseDTO {
    private List<Map<String, Object>> candidates;

    public String extractText() {
        if (candidates == null || candidates.isEmpty()) return null;

        Map<String, Object> first = candidates.get(0);
        Map<String, Object> content = (Map<String, Object>) first.get("content");

        if (content == null) return null;

        List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
        if (parts == null || parts.isEmpty()) return null;

        return parts.get(0).get("text");
    }

    public List<Map<String, Object>> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<Map<String, Object>> candidates) {
        this.candidates = candidates;
    }
}