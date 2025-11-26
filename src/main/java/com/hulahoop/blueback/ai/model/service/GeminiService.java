package com.hulahoop.blueback.ai.model.service;

import com.hulahoop.blueback.ai.model.dto.AiResponseDTO;
import com.hulahoop.blueback.ai.model.service.bike.BikeFlowRouter;
import com.hulahoop.blueback.ai.model.service.movie.MovieFlowRouter;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MovieFlowRouter movieFlowRouter;
    private final BikeFlowRouter bikeFlowRouter;

    private final Map<String, UserSession> userSessions = new HashMap<>();

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String baseUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    public GeminiService(MovieFlowRouter movieFlowRouter,
            BikeFlowRouter bikeFlowRouter) {
        this.movieFlowRouter = movieFlowRouter;
        this.bikeFlowRouter = bikeFlowRouter;
    }

    public synchronized AiResponseDTO askGemini(String prompt, String userId) {

        if (userId == null || userId.isBlank()) {
            return new AiResponseDTO("유효하지 않은 사용자입니다. 다시 로그인해주세요.");
        }

        userSessions.putIfAbsent(userId, new UserSession());
        UserSession session = userSessions.get(userId);

        session.getHistory().add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        LocalDate parsedDate = extractDateFromText(prompt);
        session.getBookingContext().put("targetDate", parsedDate.toString());

        String lower = prompt.toLowerCase().trim();

        // ✅ 0) 영화 취소 플로우 우선 처리
        if (movieFlowRouter.isInCancelFlow(userId)) {
            String result = movieFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                response.setCinemas(session.getLastCinemas());
            }
            return response;
        }

        // ✅ 1) 이미 진행 중인 상태 유지
        if (session.getStep() != UserSession.Step.IDLE) {

            // ✅ 세션이 유지되는데 flowType이 사라졌다면 자동 복구
            if (session.getFlowType() == UserSession.FlowType.NONE) {
                if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                    session.setFlowType(UserSession.FlowType.MOVIE);
                } else if (session.getLastBikes() != null && !session.getLastBikes().isEmpty()) {
                    session.setFlowType(UserSession.FlowType.BIKE);
                }
            }

            if (session.getFlowType() == UserSession.FlowType.MOVIE) {
                String result = movieFlowRouter.handle(prompt, session, userId);
                AiResponseDTO response = new AiResponseDTO(result);
                if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                    response.setCinemas(session.getLastCinemas());
                }
                return response;
            }

            if (session.getFlowType() == UserSession.FlowType.BIKE) {
                String result = bikeFlowRouter.handle(prompt, session, userId);
                AiResponseDTO response = new AiResponseDTO(result);
                if (session.getLastBikes() != null && !session.getLastBikes().isEmpty()) {
                    response.setBicycles(session.getLastBikes());
                }
                return response;
            }
        }

        // ✅ 2) 종료 의도
        if (isCancelIntent(prompt)) {
            session.reset();
            return new AiResponseDTO("대화를 종료했습니다. 필요하시면 다시 말씀해주세요.");
        }

        // ✅ 3) 명확한 구문 우선 처리

        // 영화 예약 확정 표현
        if (lower.contains("영화 예약") || lower.contains("영화 예매")) {
            session.setFlowType(UserSession.FlowType.MOVIE);
            String result = movieFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                response.setCinemas(session.getLastCinemas());
            }
            return response;
        }

        // 자전거 예약 확정 표현
        if (lower.contains("자전거 예약") ||
                lower.contains("따릉이 예약") ||
                lower.contains("바이크 예약")) {
            session.setFlowType(UserSession.FlowType.BIKE);
            String result = bikeFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastBikes() != null && !session.getLastBikes().isEmpty()) {
                response.setBicycles(session.getLastBikes());
            }
            return response;
        }

        // ✅ 4) 단독 "예약" 입력 — 흐름 시작 금지
        if (lower.equals("예약")) {
            return new AiResponseDTO(
                    "어떤 예약을 도와드릴까요?\n\n" +
                            "🎬 영화 예매\n🚲 자전거 대여\n\n말씀해주세요!");
        }

        // ✅ 5) 일반 키워드 기반 진입 (충돌 없이)
        if (containsAny(lower, List.of("자전거", "따릉이", "바이크", "전기자전거"))) {
            session.setFlowType(UserSession.FlowType.BIKE);
            String result = bikeFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastBikes() != null && !session.getLastBikes().isEmpty()) {
                response.setBicycles(session.getLastBikes());
            }
            return response;
        }

        if (containsAny(lower, List.of("영화", "예매", "상영", "시간표"))
                || prompt.matches("^\\d{10}$")) {
            session.setFlowType(UserSession.FlowType.MOVIE);
            String result = movieFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                response.setCinemas(session.getLastCinemas());
            }
            return response;
        }

        // ✅ 6) 자유 대화 모드
        return callGeminiFreeChat(session.getHistory());
    }

    private LocalDate extractDateFromText(String text) {
        if (text == null)
            return LocalDate.now();

        text = text.toLowerCase().trim();
        LocalDate today = LocalDate.now();

        if (text.contains("내일"))
            return today.plusDays(1);
        if (text.contains("모레"))
            return today.plusDays(2);

        Pattern p = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
        Matcher m = p.matcher(text);

        if (m.find()) {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            return LocalDate.of(2025, month, day);
        }

        return today;
    }

    private AiResponseDTO callGeminiFreeChat(List<Map<String, Object>> history) {
        Map<String, Object> req = Map.of("contents", history);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "?key=" + apiKey,
                    new HttpEntity<>(req, headers),
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                return new AiResponseDTO("AI 서버 오류: " + response.getStatusCode());
            }

            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> cand = (List<Map<String, Object>>) body.get("candidates");
            Map<String, Object> content = (Map<String, Object>) cand.get(0).get("content");
            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
            String text = parts.get(0).get("text");

            history.add(Map.of("role", "model", "parts", List.of(Map.of("text", text))));
            return new AiResponseDTO(text);

        } catch (Exception e) {
            return new AiResponseDTO("현재 AI 응답이 원활하지 않습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private boolean isCancelIntent(String text) {
        if (text == null)
            return false;
        String trimmed = text.trim();
        return trimmed.equals("그만") ||
                trimmed.equals("취소") ||
                trimmed.equals("끝") ||
                trimmed.equals("종료") ||
                trimmed.equals("나가기") ||
                trimmed.equals("끝내기") ||
                trimmed.equals("안할래");
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null)
            return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    public void resetConversation(String userId) {
        if (userId != null && userSessions.containsKey(userId)) {
            userSessions.get(userId).reset();
        }
    }
}
