package com.hulahoop.blueback.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiService {

    private final RestTemplate restTemplate;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String baseUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    // 💬 대화 이력 저장용 리스트 (사용자 + AI 메시지)
    private final List<Map<String, Object>> conversationHistory = new ArrayList<>();

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public synchronized String askGemini(String prompt) {
        // 사용자 메시지를 이력에 추가
        Map<String, Object> userPart = Map.of("text", prompt);
        conversationHistory.add(Map.of("role", "user", "parts", List.of(userPart)));

        // 요청 body 생성 — 지금까지의 전체 대화 context를 포함
        Map<String, Object> requestBody = Map.of("contents", conversationHistory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String urlWithKey = baseUrl + "?key=" + apiKey;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(urlWithKey, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");

                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                    List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");

                    if (parts != null && !parts.isEmpty()) {
                        String aiResponse = parts.get(0).get("text");

                        // 🔹 AI 답변도 대화 이력에 추가
                        Map<String, Object> aiPart = Map.of("text", aiResponse);
                        conversationHistory.add(Map.of("role", "model", "parts", List.of(aiPart)));

                        return aiResponse;
                    }
                }
            }

            return "AI 응답 형식 오류";

        } catch (Exception e) {
            return "AI 호출 중 오류 발생: " + e.getMessage();
        }
    }

    // 🧹 대화 리셋 API용
    public void resetConversation() {
        conversationHistory.clear();
    }
}
