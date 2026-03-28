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

// 마이크로서비스 아키텍처(MSA)에서 AI 서버 혼자서 영화, 자전거 DB를 다 까볼 수 없기 때문에,
// 게이트웨이(8080)로 "얘가 영화 예매하고 싶대 처리좀!" 이라고 던지는 창구 역할을 함
@Service
public class IntentService {

    private static final Logger log = LoggerFactory.getLogger(IntentService.class);
    // 보통 RestTemplate을 쓰지만, MSA 비동기 통신 체계에서는 WebFlux의 WebClient가 성능/안정성 방면에서 좋음
    private final WebClient webClient;

    public IntentService(WebClient.Builder webClientBuilder) {
        // 모든 뒷단 MSA 통신은 8080 포트(Gateway)를 통해 나가도록 기본 BaseUrl 박아둠
        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8080")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // 어떤 의도(intent) 문자열과 어떤 데이터(data) 모델을 들고 Gateway에 쏘는 범용성 모듈
    public Map<String, Object> processIntent(String intent, Map<String, Object> data) {
        // Dispatch(배차, 파견) 시킨다는 뜻의 통일된 규격 경로 사용
        final String gatewayUri = "/api/gateway/dispatch";

        // 개발자가 실수로 intent를 안 보냈을 때 방어
        if (intent == null || intent.isBlank()) {
            return Map.of("error", "X-Intent 값이 비어 있음");
        }

        // 바디 껍데기 세팅 - { "intent": "movie_booking", "data": { ... } } 이런 꼴로 랩핑해서 보냄
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("intent", intent);
        requestBody.put("data", data != null ? data : Map.of());

        try {
            // WebClient를 이용해 논블로킹, 혹은 블로킹(.block()) 방식으로 HTTP 요청 발생
            Map<String, Object> result = webClient.post()
                    .uri(gatewayUri)
                    //  중요 포인트: Gateway는 바디를 뜯어보기 전 필터 단계에서 이 'intent' 헤더값을 훔쳐보고 라우팅(어떤 서버로 갈지)을
                    // 결정함
                    .header("intent", intent)
                    .bodyValue(requestBody)
                    // retrieve()는 결과값을 빼오겠다는 선언, bodyToMono는 결과 JSON을 자바 Map으로 변환하겠다는 의미
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5)) // 5초 지날 때까지 답 없으면 통신 에러 처리(타임아웃) 방어
                    .onErrorResume(ex -> Mono.just(Map.of(
                            "error", "게이트웨이 호출 실패: " + ex.getMessage())))
                    .block(); // 비동기로 짜여있지만 이 시스템은 동기적 흐름이 편하므로 강제로 block 걸어서 결과 올때까지 멈추게 함

            // 디버깅 용도로 보냈던 요청값과 받은 결과값을 로깅
            log.info("Gateway Response for intent '{}': {}", intent, result);
            log.info("Sending to gateway: {}", requestBody);
            log.info("intent: {}", intent);
            log.info("data: {}", data);

            return result != null ? result : Map.of("error", "Empty response from gateway");
        } catch (Exception e) {
            // 게이트웨이가 아예 죽었거나 연결이 끊어졌을 때의 치명적 예외처리
            return Map.of("error", "Failed to call " + gatewayUri + ": " + e.getMessage());
        }
    }
}
