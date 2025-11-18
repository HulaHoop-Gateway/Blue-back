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

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        /** 🔥 1) 날짜 먼저 추출해서 세션에 저장 */
        LocalDate parsedDate = extractDateFromText(prompt);
        session.getBookingContext().put("targetDate", parsedDate.toString());

        /** 🔥 0️⃣ 취소 플로우 중인지 먼저 확인 — 가장 중요 */
        if (movieFlowRouter.isInCancelFlow(userId)) {
            String movieResponse = movieFlowRouter.handle(prompt, session, userId);
            return new AiResponseDTO(movieResponse);
        }

        /** ⭐ 이미 영화 플로우(예매 흐름) 중이면 계속 영화 흐름 유지 */
        if (session.getStep() != UserSession.Step.IDLE) {
            String movieResponse = movieFlowRouter.handle(prompt, session, userId);
            return new AiResponseDTO(movieResponse);
        }

        /** 🔥 종료 키워드 */
        if (isCancelIntent(prompt)) {
            session.reset();
            return new AiResponseDTO("✅ 대화를 종료했습니다. 다른 도움이 필요하시면 말씀해주세요.");
        }

        /** 🚲 자전거 */
        if (containsAny(prompt, List.of("자전거", "대여", "반납", "따릉이"))) {
            List<BikeDTO> bikeDTOs = bikeFlowHandler.handleBikeFlow(prompt, session);
            if (bikeDTOs != null && !bikeDTOs.isEmpty()) {
                return new AiResponseDTO(null, bikeDTOs);
            } else if (bikeDTOs != null) {
                return new AiResponseDTO("🚲 대여 가능한 자전거가 없습니다.");
            }
        }

        /** 🎬 영화 플로우 시작 조건 */
        if (containsAny(prompt, List.of("영화", "예매", "예약", "상영", "시간표"))
                || prompt.matches("^\\d{10}$")    // ⭐ 예매번호 입력도 영화 플로우로 연결
        ) {
            String movieResponse = movieFlowRouter.handle(prompt, session, userId);
            return new AiResponseDTO(movieResponse);
        }

        /** 🎤 자유 대화 */
        return callGeminiFreeChat(session.getHistory());
    }

    // ------------------ 🔥 날짜 추출 함수 ------------------

    private LocalDate extractDateFromText(String text) {
        if (text == null) return LocalDate.now();

        text = text.toLowerCase().trim();
        LocalDate today = LocalDate.now();

        // 내일 / 모레
        if (text.contains("내일")) return today.plusDays(1);
        if (text.contains("모레")) return today.plusDays(2);

        // 예: "11월 20일"
        Pattern p = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
        Matcher m = p.matcher(text);

        if (m.find()) {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            return LocalDate.of(2025, month, day); // 고정: 2025년
        }

        // 기본: 오늘
        return today;
    }

    // ------------------ 자유 대화 처리 ------------------
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
                    List<Map<String, Object>> cand = (List<Map<String, Object>>) body.get("candidates");
                    Map<String, Object> content = (Map<String, Object>) cand.get(0).get("content");
                    List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                    String text = parts.get(0).get("text");

                    history.add(Map.of("role", "model", "parts", List.of(Map.of("text", text))));
                    return new AiResponseDTO(text);
                }

                if (response.getStatusCode().value() == 503) {
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

        return new AiResponseDTO("⚠️ Gemini 응답이 없습니다.");
    }

    // ------------------ 공통 유틸 ------------------

    private boolean isCancelIntent(String text) {
        if (text == null) return false;
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
        if (text == null) return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    public void resetConversation(String userId) {
        if (userId != null && userSessions.containsKey(userId)) {
            userSessions.get(userId).reset();
        }
    }
}
