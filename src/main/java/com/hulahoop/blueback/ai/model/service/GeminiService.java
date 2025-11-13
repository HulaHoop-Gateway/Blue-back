package com.hulahoop.blueback.ai.model.service;

import com.hulahoop.blueback.ai.model.service.bike.BikeFlowHandler;
import com.hulahoop.blueback.ai.model.service.movie.MovieFlowRouter;
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
    private final MovieFlowRouter movieFlowRouter;
    private final BikeFlowHandler bikeFlowHandler;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String baseUrl =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    public GeminiService(
            RestTemplate restTemplate,
            IntentService intentService,
            MovieFlowRouter movieFlowRouter,
            BikeFlowHandler bikeFlowHandler
    ) {
        this.restTemplate = restTemplate;
        this.intentService = intentService;
        this.movieFlowRouter = movieFlowRouter;
        this.bikeFlowHandler = bikeFlowHandler;
    }

    public synchronized String askGemini(String prompt, String userId) {
        if (userId == null || userId.isBlank()) {
            return "❌ 유효하지 않은 사용자입니다. 다시 로그인해주세요.";
        }

        userSessions.putIfAbsent(userId, new UserSession());
        UserSession session = userSessions.get(userId);

        session.getHistory().add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        String digitsOnly = prompt.replaceAll("[^0-9]", "");
        if (digitsOnly.length() == 10) {
            Map<String, Object> res = intentService.processIntent("movie_cancel_step2", Map.of("reservationNum", digitsOnly));
            if (res.containsKey("message")) {
                return res.get("message").toString();
            } else {
                return "❌ 예매 정보를 확인할 수 없습니다.";
            }
        }

        // ✅ 긍정 응답 → 예매 취소 처리
        List<String> positiveResponses = List.of("네", "예", "응", "그래", "좋아", "ㅇㅇ", "오케이");
        if (positiveResponses.stream().anyMatch(p -> p.equalsIgnoreCase(prompt.trim()))) {
            String lastReservationNum = extractLastReservationNum(session);
            if (lastReservationNum != null) {
                Map<String, Object> res = intentService.processIntent("movie_cancel_step3", Map.of("reservationNum", lastReservationNum));
                return res.getOrDefault("message", "⚠️ 예매 취소 처리 중 오류가 발생했습니다.").toString();
            } else {
                return "❌ 취소할 예매 번호를 찾을 수 없습니다. 먼저 예매 번호를 입력해주세요.";
            }
        }

        // ✅ 부정 응답 → 예매 취소 중단
        List<String> negativeResponses = List.of("아니오", "취소", "안할래", "그만", "아니", "안돼");
        if (negativeResponses.stream().anyMatch(p -> p.equalsIgnoreCase(prompt.trim()))) {
            session.reset();
            return "🚫 예매 취소가 중단되었습니다. 다른 작업을 원하시면 말씀해주세요.";
        }

        // 🚫 취소 명령 처리
        if (isCancelIntent(prompt)) {
            session.reset();
            return "✅ 예약이 취소되었습니다. 다른 도움이 필요하신가요?";
        }

        // 🚲 자전거 흐름
        String bikeRes = bikeFlowHandler.handleBikeFlow(prompt, session);
        if (bikeRes != null) return bikeRes;

        // 🎬 영화 흐름
        String movieReply = movieFlowRouter.handle(prompt, session, userId);
        if (movieReply != null) return movieReply;

        // ✨ 일반 대화
        return callGeminiFreeChat(session.getHistory());
    }

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

    private boolean isCancelIntent(String t) {
        return t != null && (t.contains("그만") || t.contains("안할래"));
    }

    public void resetConversation(String userId) {
        if (userId != null && !userId.isBlank()) {
            userSessions.remove(userId);
        }
    }

    // 🔍 세션에서 마지막 예매 번호 추출
    private String extractLastReservationNum(UserSession session) {
        List<Map<String, Object>> history = session.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> entry = history.get(i);
            List<Map<String, String>> parts = (List<Map<String, String>>) entry.get("parts");
            for (Map<String, String> part : parts) {
                String text = part.get("text");
                String digits = text.replaceAll("[^0-9]", "");
                if (digits.length() == 10) return digits;
            }
        }
        return null;
    }
}
