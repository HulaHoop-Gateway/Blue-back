package com.hulahoop.blueback.ai.model.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class IntentService {

    private static final Logger log = LoggerFactory.getLogger(IntentService.class);
    private final WebClient webClient;


    public IntentService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8080")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Map<String, Object> processIntent(String intent, Map<String, Object> data) {
        final String gatewayUri = "/api/gateway/dispatch";

        if (intent == null || intent.isBlank()) {
            return Map.of("error", "X-Intent 값이 비어 있음");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("intent", intent);
        requestBody.put("data", data != null ? data : Map.of());

        try {
            Map<String, Object> result = webClient.post()
                    .uri(gatewayUri)
                    .header("intent", intent)                          // ✅ 헤더 분기
                    .bodyValue(requestBody)           // ✅ 래핑 금지! 루트로 전송
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorResume(ex -> Mono.just(Map.of(
                            "error", "게이트웨이 호출 실패: " + ex.getMessage()
                    )))
                    .block();

            // 🔎 게이트웨이 응답 로깅
            log.info("Gateway Response for intent '{}': {}", intent, result);
            log.info("📤 Sending to gateway: {}", requestBody);
            log.info("🧪 intent: {}", intent);
            log.info("🧪 data: {}", data);


            return result != null ? result : Map.of("error", "Empty response from gateway");
        } catch (Exception e) {
            return Map.of("error", "Failed to call " + gatewayUri + ": " + e.getMessage());
        }
    }
}
