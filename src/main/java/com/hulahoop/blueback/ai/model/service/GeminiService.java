package com.hulahoop.blueback.ai.model.service;

import com.hulahoop.blueback.ai.model.service.bike.BikeFlowHandler;
import com.hulahoop.blueback.ai.model.service.movie.MovieFlowHandler;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeminiService {

    private final RestTemplate restTemplate;
    private final IntentService intentService;
    private final MovieFlowHandler movieFlowHandler;
    private final BikeFlowHandler bikeFlowHandler;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String baseUrl =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    public GeminiService(RestTemplate restTemplate, IntentService intentService) {
        this.restTemplate = restTemplate;
        this.intentService = intentService;
        this.movieFlowHandler = new MovieFlowHandler(intentService);
        this.bikeFlowHandler = new BikeFlowHandler(intentService);
    }

    /**
     * AI에게 사용자 입력을 전달하고, 현재 세션 상태에 따라 응답을 생성.
     * 인증된 사용자만 접근 가능하므로 userId는 필수.
     */
    public synchronized String askGemini(String prompt, String userId) {
        if (userId == null || userId.isBlank()) {
            return "❌ 유효하지 않은 사용자입니다. 다시 로그인해주세요.";
        }

        // 세션 초기화 or 유지
        userSessions.putIfAbsent(userId, new UserSession());
        UserSession session = userSessions.get(userId);

        session.getHistory().add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        // 🚫 취소 명령 처리
        if (isCancelIntent(prompt)) {
            session.reset();
            return "✅ 예약이 취소되었습니다. 다른 도움이 필요하신가요?";
        }

        // 🚲 자전거 흐름
        String bikeRes = bikeFlowHandler.handleBikeFlow(prompt, session);
        if (bikeRes != null) return bikeRes;

        // 🎬 영화 흐름
        String movieReply = movieFlowHandler.handleMovieFlow(prompt, session, userId);
        if (movieReply != null) return movieReply;

        // ✨ 일반 대화 (AI 자유 대화)
        return callGeminiFreeChat(session.getHistory());
    }

    /**
     * Gemini API를 직접 호출하는 메서드
     */
    private String callGeminiFreeChat(List<Map<String, Object>> history) {
        Map<String, Object> req = Map.of("contents", history);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(baseUrl + "?key=" + apiKey, new HttpEntity<>(req, headers), Map.class);

            List<Map<String, Object>> cand = (List<Map<String, Object>>) response.getBody().get("candidates");
            Map<String, Object> content = (Map<String, Object>) cand.get(0).get("content");
            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
            String text = parts.get(0).get("text");

            history.add(Map.of("role", "model", "parts", List.of(Map.of("text", text))));
            return text;

        } catch (Exception e) {
            return "AI 호출 오류: " + e.getMessage();
        }
    }

    /**
     * 좌석 선택 완료 처리
     */
    public String completeSeatSelection(String userId) {
        if (userId == null || userId.isBlank()) {
            return "❌ 유효하지 않은 사용자입니다.";
        }

        UserSession session = userSessions.get(userId);
        if (session == null) return "⚠️ 세션이 만료되었습니다. 다시 시도해주세요.";

        session.reset();
        return "✅ 좌석 선택이 완료되었습니다!\n💳 10분 내 결제해주세요.";
    }

    /**
     * 취소 명령어 감지
     */
    private boolean isCancelIntent(String t) {
        return t != null && (t.contains("취소") || t.contains("그만") || t.contains("안할래"));
    }

    /**
     * 세션 초기화
     */
    public void resetConversation(String userId) {
        if (userId != null && !userId.isBlank()) {
            userSessions.remove(userId);
        }
    }
}
