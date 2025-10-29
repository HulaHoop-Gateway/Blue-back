package com.hulahoop.blueback.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiService {

    private final RestTemplate restTemplate;
    private final IntentService intentService; // ✅ 게이트웨이 연동

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String baseUrl =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    // 💬 대화 이력 저장 (AI 컨텍스트용)
    private final List<Map<String, Object>> conversationHistory = new ArrayList<>();

    public GeminiService(RestTemplate restTemplate, IntentService intentService) {
        this.restTemplate = restTemplate;
        this.intentService = intentService;
    }

    // 🎯 메인 진입점
    public synchronized String askGemini(String prompt) {
        // ✅ 사용자 입력 저장
        conversationHistory.add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        // ✅ 1️⃣ intent 감지
        String intent = extractIntent(prompt);
        System.out.println("[GeminiService] Detected intent: " + intent);

        // ✅ 2️⃣ intent 감지 시 → 게이트웨이로 요청
        if (intent != null) {
            Map<String, Object> data = parseUserInput(prompt);
            Map<String, Object> gatewayResult = intentService.processIntent(intent, data);
            System.out.println("[GeminiService] Gateway result: " + gatewayResult);

            // ✅ 게이트웨이 응답이 있으면 즉시 반환
            if (gatewayResult != null && !gatewayResult.isEmpty()) {
                String formatted = formatResponse(intent, gatewayResult);
                return formatted != null ? formatted : "DB 결과를 불러오지 못했습니다.";
            }
        }

        // ✅ 3️⃣ intent가 없을 경우에만 Gemini API 호출
        Map<String, Object> requestBody = Map.of("contents", conversationHistory);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String urlWithKey = baseUrl + "?key=" + apiKey;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(urlWithKey, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");

                    if (parts != null && !parts.isEmpty()) {
                        String aiResponse = parts.get(0).get("text");
                        conversationHistory.add(Map.of("role", "model", "parts", List.of(Map.of("text", aiResponse))));
                        return aiResponse;
                    }
                }
            }
            return "AI 응답 형식 오류";

        } catch (Exception e) {
            return "AI 호출 중 오류 발생: " + e.getMessage();
        }
    }

    // 🧩 intent 추출 로직 (자연어 인식 강화)
    private String extractIntent(String prompt) {
        String lower = prompt.toLowerCase();

        // 🎬 영화 관련
        if (lower.contains("영화") && (lower.contains("예약") || lower.contains("예매"))) return "movie_booking_step1";
        if (lower.contains("노바시네마") || lower.contains("상영작")) return "movie_booking_step2";
        if (lower.contains("좌석") || lower.contains("자리")) return "movie_booking_step3";

        // 🚲 자전거 관련
        if (lower.contains("자전거") && lower.contains("대여")) return "bike_rental_step1";
        if (lower.contains("자전거") && lower.contains("반납")) return "bike_rental_step2";

        return null;
    }

    // 🧠 사용자 입력에서 지점명/영화명 자동 추출
    private Map<String, Object> parseUserInput(String prompt) {
        Map<String, Object> data = new HashMap<>();
        data.put("userMessage", prompt);

        // 지점명 추출
        if (prompt.contains("하남")) data.put("branchName", "노바시네마 하남1점");
        else if (prompt.contains("강남")) data.put("branchName", "노바시네마 강남점");
        else if (prompt.contains("잠실")) data.put("branchName", "노바시네마 잠실점");

        // 영화명 (추후 자연어 파서 추가 가능)
        if (prompt.contains("비밀") || prompt.contains("조각")) data.put("movieTitle", "비밀의 숲");

        return data;
    }

    // 🎨 게이트웨이(DB) 응답을 자연스러운 문장으로 변환
    private String formatResponse(String intent, Map<String, Object> result) {
        if (result == null || result.isEmpty()) return "📭 조회 결과가 없습니다.";

        switch (intent) {
            // 🎬 Step1: 영화관 목록
            case "movie_booking_step1": {
                List<Map<String, Object>> cinemas = (List<Map<String, Object>>) result.get("cinemas");
                if (cinemas == null || cinemas.isEmpty()) return "🎥 가까운 영화관 정보를 찾지 못했습니다.";

                StringBuilder sb = new StringBuilder("🎬 등록된 노바시네마 지점 목록입니다:\n");
                for (Map<String, Object> c : cinemas) {
                    sb.append(" - ").append(c.get("branch_name"))  // ✅ 수정됨
                            .append(" (").append(c.get("address")).append(")\n");
                }
                return sb.toString();
            }

            // 🎥 Step2: 상영 영화 목록 (오늘 이후 5일)
            case "movie_booking_step2": {
                List<Map<String, Object>> movies = (List<Map<String, Object>>) result.get("movies");
                if (movies == null || movies.isEmpty()) return "해당 지점의 상영 중인 영화가 없습니다.";

                StringBuilder sb = new StringBuilder("📽 현재 상영 중인 영화 일정입니다 (오늘 포함 5일치):\n");
                for (Map<String, Object> m : movies) {
                    sb.append(" - ").append(m.get("movie_title"))
                            .append(" | 등급: ").append(m.get("audience_rating"))
                            .append(" | 상영일시: ").append(m.get("screening_date")).append("\n");
                }
                return sb.toString();
            }

            // 💺 Step3: 좌석 현황
            case "movie_booking_step3": {
                List<Map<String, Object>> seats = (List<Map<String, Object>>) result.get("seats");
                if (seats == null || seats.isEmpty()) return "좌석 정보를 불러오지 못했습니다.";

                StringBuilder sb = new StringBuilder("💺 좌석 현황입니다:\n");
                for (Map<String, Object> s : seats) {
                    sb.append(" - ").append(s.get("seat"))
                            .append(": ").append(((Integer) s.get("available") == 1) ? "가능" : "예약됨").append("\n");
                }
                return sb.toString();
            }

            // 🚲 Step4: 자전거 관련
            case "bike_rental_step1": {
                List<Map<String, Object>> bikes = (List<Map<String, Object>>) result.get("bicycles");
                if (bikes == null || bikes.isEmpty()) return "🚲 현재 대여 가능한 자전거가 없습니다.";

                StringBuilder sb = new StringBuilder("🚴‍♂️ 인근 대여 가능한 자전거 목록입니다:\n");
                for (Map<String, Object> b : bikes) {
                    sb.append(" - [")
                            .append(b.get("bicycleCode"))   // ✅ 자전거 코드 추가
                            .append("] ")
                            .append(b.get("bicycleType"))   // ✅ 자전거 종류
                            .append(" (상태: ").append(b.get("status")).append(")\n");
                }
                return sb.toString();
            }


            case "bike_rental_step2":
                return "✅ 자전거 반납이 완료되었습니다.";

            default:
                return "게이트웨이 응답: " + result.toString();
        }
    }

    // 🧹 대화 리셋
    public void resetConversation() {
        conversationHistory.clear();
    }
}
