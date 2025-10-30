package com.hulahoop.blueback.ai.model.service;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class IntentService {

    private final RestTemplate restTemplate;

    public IntentService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    // ✅ 오직 게이트웨이로만 전달 (게이트웨이가 intent를 분기함)
    public Map<String, Object> processIntent(String intent, Map<String, Object> data) {
        String gatewayUrl = "http://localhost:8080/api/gateway/dispatch";  // ✅ 게이트웨이 엔드포인트

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("intent", intent);
        requestBody.put("data", data);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(gatewayUrl, requestBody, Map.class);
            return response.getBody();
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to call " + gatewayUrl + ": " + e.getMessage());
            return error;
        }
    }
}
