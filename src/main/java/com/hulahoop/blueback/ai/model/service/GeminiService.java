package com.hulahoop.blueback.ai.model.service;

import com.hulahoop.blueback.ai.model.dto.AiResponseDTO;
import com.hulahoop.blueback.ai.model.dto.BikeDTO;
import com.hulahoop.blueback.ai.model.service.bike.BikeFlowHandler;
import com.hulahoop.blueback.ai.model.service.movie.MovieFlowRouter;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MovieFlowRouter movieFlowRouter;
    private final BikeFlowHandler bikeFlowHandler;

    private final Map<String, UserSession> userSessions = new HashMap<>();

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String baseUrl =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    public GeminiService(MovieFlowRouter movieFlowRouter, BikeFlowHandler bikeFlowHandler) {
        this.movieFlowRouter = movieFlowRouter;
        this.bikeFlowHandler = bikeFlowHandler;
    }

    /**
     * 사용자 입력을 받아 적절한 흐름으로 전달하는 핵심 메서드
     */
    public synchronized AiResponseDTO askGemini(String prompt, String userId) {
        if (userId == null || userId.isBlank()) {
            return new AiResponseDTO("❌ 유효하지 않은 사용자입니다. 다시 로그인해주세요.");
        }

        userSessions.putIfAbsent(userId, new UserSession());
        UserSession session = userSessions.get(userId);

        session.getHistory().add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        // ✅ 이미 특정 플로우(영화 예매, 자전거 등) 진행 중이라면 해당 핸들러로만 진행
        if (session.getStep() != UserSession.Step.IDLE) {
            System.out.println("🔄 현재 플로우 진행 중: " + session.getStep());
            String movieResponse = movieFlowRouter.handle(prompt, session, userId);
            return new AiResponseDTO(movieResponse); // Assuming movieFlowRouter.handle still returns String
        }

        // 🚫 대화 종료 요청
        if (isCancelIntent(prompt)) {
            session.reset();
            return new AiResponseDTO("✅ 대화를 종료했습니다. 다른 도움이 필요하시면 말씀해주세요.");
        }

        // 🚲 자전거 관련 플로우 감지
        if (containsAny(prompt, List.of("자전거", "대여", "반납", "따릉이"))) {
            List<BikeDTO> bikeDTOs = bikeFlowHandler.handleBikeFlow(prompt, session);
            if (bikeDTOs != null && !bikeDTOs.isEmpty()) {
                return new AiResponseDTO(null, bikeDTOs); // Return bikes, no message
            } else if (bikeDTOs != null && bikeDTOs.isEmpty()) {
                return new AiResponseDTO("🚲 대여 가능한 자전거가 없습니다.");
            }
        }

        // 🎬 영화 관련 플로우 감지
        if (containsAny(prompt, List.of("영화", "예매", "예약", "상영", "시간표", "취소"))) {
            String movieResponse = movieFlowRouter.handle(prompt, session, userId);
            if (movieResponse != null && !movieResponse.isBlank()) return new AiResponseDTO(movieResponse);
        }

        // 💬 자유 대화 (플로우 외 상태에서만 실행)
        return callGeminiFreeChat(session.getHistory());
    }

    /**
     * Gemini 모델 호출 (자동 재시도 포함)
     */
    private AiResponseDTO callGeminiFreeChat(List<Map<String, Object>> history) {
        Map<String, Object> req = Map.of("contents", history);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        baseUrl + "?key=" + apiKey,
                        new HttpEntity<>(req, headers),
                        Map.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    Map<String, Object> body = response.getBody();
                    if (body == null || !body.containsKey("candidates"))
                        throw new RuntimeException("빈 응답을 받았습니다.");

                    List<Map<String, Object>> cand = (List<Map<String, Object>>) body.get("candidates");
                    Map<String, Object> content = (Map<String, Object>) cand.get(0).get("content");
                    List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                    String text = parts.get(0).get("text");

                    history.add(Map.of("role", "model", "parts", List.of(Map.of("text", text))));
                    return new AiResponseDTO(text);
                }

                // 🔁 503 과부하일 경우 재시도
                if (response.getStatusCode().value() == 503) {
                    System.out.println("⚠️ Gemini 서버 과부하, 재시도 중... (" + attempt + ")");
                    Thread.sleep(1000);
                    continue;
                }

                return new AiResponseDTO("⚠️ AI 서버 응답 오류: " + response.getStatusCode());

            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    return new AiResponseDTO("🚧 현재 AI 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.");
                }
            }
        }

        return new AiResponseDTO("⚠️ Gemini 응답이 없습니다. 서버가 일시적으로 과부하 상태입니다.");
    }

    /** 플로우 종료 문장 감지 */
    private boolean isCancelIntent(String text) {
        if (text == null) return false;
        return List.of("그만", "취소", "끝", "종료", "나가기", "끝내기")
                .stream().anyMatch(text::contains);
    }

    /** 단어 포함 여부 체크 */
    private boolean containsAny(String text, List<String> keywords) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }
    /** 대화 세션(히스토리 + 단계) 초기화 */
    public void resetConversation(String userId) {
        if (userId != null && userSessions.containsKey(userId)) {
            userSessions.get(userId).reset();
            System.out.println("🧹 [" + userId + "] 대화 세션 초기화 완료");
        }
    }

}
