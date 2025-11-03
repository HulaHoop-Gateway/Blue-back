// src/main/java/com/hulahoop/blueback/ai/model/service/GeminiService.java
package com.hulahoop.blueback.ai.model.service;

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

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String baseUrl =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    /** 유저별 세션 저장 (스레드 안전) */
    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    private static class UserSession {
        List<Map<String, Object>> history = new ArrayList<>();
        Step step = Step.IDLE;
        Map<String, Object> bookingContext = new HashMap<>();
        List<Map<String, Object>> lastCinemas = new ArrayList<>();
        List<Map<String, Object>> lastMovies = new ArrayList<>();
        List<Map<String, Object>> lastSeats = new ArrayList<>();
    }

    private enum Step { IDLE, BRANCH_SELECT, MOVIE_SELECT, SEAT_SELECT }

    public GeminiService(RestTemplate restTemplate, IntentService intentService) {
        this.restTemplate = restTemplate;
        this.intentService = intentService;
    }

    /**
     * 유저별 히스토리 적용된 askGemini
     * @param prompt 유저 입력
     * @param userId 유저 아이디 (Principal.getName())
     */
    public synchronized String askGemini(String prompt, String userId) {
        if (userId == null || userId.isBlank()) userId = "guest";

        userSessions.putIfAbsent(userId, new UserSession());
        UserSession session = userSessions.get(userId);

        // 대화 히스토리 저장
        session.history.add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        // 취소 처리
        if (isCancelIntent(prompt)) {
            resetFlow(session);
            return "✅ 예약이 취소되었습니다. 다른 도움이 필요하신가요?";
        }

        // 자전거 관련 즉시 응답
        String bikeRes = handleBikeIntent(prompt);
        if (bikeRes != null) {
            // 모델 히스토리에 봇 응답도 추가하면 좋음
            session.history.add(Map.of("role", "model", "parts", List.of(Map.of("text", bikeRes))));
            return bikeRes;
        }

        // 영화 예약 상태머신 처리 (userId 전달)
        String movieReply = handleMovieFlow(prompt, session, userId);
        if (movieReply != null) {
            session.history.add(Map.of("role", "model", "parts", List.of(Map.of("text", movieReply))));
            return movieReply;
        }

        // 자유대화: Gemini 호출 (session.history 사용)
        String aiReply = callGeminiFreeChat(session.history);
        // 이미 callGeminiFreeChat이 히스토리에 모델 응답을 추가함
        return aiReply;
    }

    /* ------------------- 영화 상태 머신 ------------------- */
    private String handleMovieFlow(String userInput, UserSession s, String userId) {

        if (s.step == Step.IDLE && isStartBookingIntent(userInput)) {
            Map<String, Object> res = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(res.get("cinemas"));

            s.lastCinemas = cinemas;
            s.step = Step.BRANCH_SELECT;
            return formatCinemas(cinemas) + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        if (s.step == Step.BRANCH_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, s.lastCinemas.size());
            if (idx == null) return "⚠️ 지점 번호를 다시 입력해주세요. 예) 1번";

            String branchName = String.valueOf(s.lastCinemas.get(idx - 1).get("branch_name"));
            s.bookingContext.put("branchName", branchName);

            Map<String, Object> res = intentService.processIntent("movie_booking_step2", Map.of("branchName", branchName));
            List<Map<String, Object>> movies = safeList(res.get("movies"));

            s.lastMovies = movies;
            s.step = Step.MOVIE_SELECT;
            return "🎬 선택한 지점: " + branchName + "\n\n" + formatMovies(movies) + "\n예매할 영화 번호를 입력해주세요. 예) 2번";
        }

        if (s.step == Step.MOVIE_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, s.lastMovies.size());
            if (idx == null) return "⚠️ 영화 번호를 다시 입력해주세요. 예) 2번";

            Map<String, Object> selected = s.lastMovies.get(idx - 1);
            Integer scheduleNum = extractScheduleNum(selected);
            if (scheduleNum == null) return "회차 번호 오류";

            Map<String, Object> movieCtx = new HashMap<>();
            movieCtx.put("movieTitle", selected.get("movieTitle"));
            movieCtx.put("screeningDate", selected.get("screeningDate"));
            movieCtx.put("scheduleNum", scheduleNum);
            s.bookingContext.put("selectedMovie", movieCtx);

            Map<String, Object> res = intentService.processIntent("movie_booking_step3", Map.of("scheduleNum", scheduleNum));
            List<Map<String, Object>> seats = safeList(res.get("seats"));

            s.lastSeats = seats;
            s.step = Step.SEAT_SELECT;
            return "🎞 선택 영화: " + selected.get("movieTitle") + "\n상영일시: " + selected.get("screeningDate")
                    + "\n\n" + formatSeats(seats) + "\n좌석번호를 입력해주세요. 예) A1, A2";
        }

        if (s.step == Step.SEAT_SELECT) {
            List<String> reqSeats = parseSeats(userInput);
            if (reqSeats.isEmpty()) return "⚠️ 좌석 형식 오류. 예) A1, A2";

            Map<String, Object> movieCtx = safeMap(s.bookingContext.get("selectedMovie"));
            Integer scheduleNum = toInt(movieCtx.get("scheduleNum"));
            if (scheduleNum == null) return "회차 정보가 없습니다.";

            String memberName = userId; // <-- 로그인된 사용자 ID 사용

            for (String t : reqSeats) {
                Map<String, Object> seat = s.lastSeats.stream()
                        .filter(x -> t.equalsIgnoreCase(String.valueOf(x.get("seat"))))
                        .findFirst().orElse(null);

                if (seat == null) return "❌ " + t + " 좌석 없음";
                if (toInt(seat.get("available")) != 1) return "❌ " + t + " 예약 불가";

                Integer seatCode = extractSeatCode(seat);
                if (seatCode == null) return "좌석 코드가 없습니다.";

                // 실제 예약 처리 (intent service로 보냄)
                intentService.processIntent("movie_booking_step4",
                        Map.of("scheduleNum", scheduleNum, "seatCode", seatCode, "memberName", memberName));
            }

            resetFlow(s);
            return "✅ 좌석 예약 완료!\n10분 내 결제 진행해주세요.";
        }

        return null;
    }

    /* ------------------- Free Chat (Gemini 호출) ------------------- */
    private String callGeminiFreeChat(List<Map<String, Object>> history) {
        Map<String, Object> req = Map.of("contents", history);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(baseUrl + "?key=" + apiKey, new HttpEntity<>(req, headers), Map.class);

            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) Objects.requireNonNull(response.getBody()).get("candidates");

            Map<String, Object> cand = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) cand.get("content");
            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
            String text = parts.get(0).get("text");

            history.add(Map.of("role", "model", "parts", List.of(Map.of("text", text))));
            return text;

        } catch (Exception e) {
            return "AI 호출 오류: " + e.getMessage();
        }
    }

    /* ------------------- 세션 유틸 ------------------- */
    private void resetFlow(UserSession s) {
        s.step = Step.IDLE;
        s.bookingContext.clear();
        s.lastCinemas.clear();
        s.lastMovies.clear();
        s.lastSeats.clear();
        s.history.clear();
    }

    public void resetConversation(String userId) {
        if (userId == null || userId.isBlank()) userId = "guest";
        userSessions.remove(userId);
    }

    /* ------------------- 공통 유틸 ------------------- */
    private boolean isStartBookingIntent(String t) {
        t = (t == null) ? "" : t.toLowerCase();
        return (t.contains("영화") && t.contains("예약")) || t.contains("예매");
    }

    private boolean isCancelIntent(String t) {
        return t != null && (t.contains("취소") || t.contains("그만") || t.contains("안할래"));
    }

    private String handleBikeIntent(String t) {
        if (t == null) return null;
        String s = t.toLowerCase();
        if (s.contains("자전거") && (s.contains("대여") || s.contains("예약"))) {

            Map<String, Object> r = intentService.processIntent("bike_list", Map.of());
            List<Map<String, Object>> bikes = safeList(r.get("bicycles"));

            if (bikes.isEmpty()) return "🚲 대여 가능한 자전거가 없습니다.";

            StringBuilder sb = new StringBuilder("[대여 가능 자전거]\n\n");
            int i = 1;
            for (Map<String, Object> b : bikes) {
                sb.append(i++).append(". 번호: ").append(b.get("bicycleCode")).append("\n")
                        .append("   종류: ").append(b.get("bicycleType")).append("\n")
                        .append("   상태: ").append(b.get("status")).append("\n")
                        .append("   위치: ").append(b.get("latitude")).append(", ").append(b.get("longitude")).append("\n\n");
            }
            return sb.toString().trim();
        }
        return null;
    }

    private String formatCinemas(List<Map<String, Object>> l) {
        StringBuilder s = new StringBuilder("📍 가까운 영화관 목록\n\n");
        int i = 1;
        for (Map<String, Object> c : l)
            s.append(i++).append(") ").append(c.get("branch_name")).append(" - ").append(c.get("address")).append("\n");
        return s.toString();
    }

    private String formatMovies(List<Map<String, Object>> l) {
        StringBuilder s = new StringBuilder("[상영 영화 목록]\n\n");
        int i = 1;
        for (Map<String, Object> m : l)
            s.append(i++).append(". ").append(m.get("movieTitle")).append("\n   시간: ").append(m.get("screeningDate")).append("\n\n");
        return s.toString();
    }

    private String formatSeats(List<Map<String, Object>> l) {
        StringBuilder s = new StringBuilder("[좌석 현황]\n\n");
        int i = 1;
        for (Map<String, Object> x : l) {
            s.append(x.get("seat")).append(" (")
                    .append(toInt(x.get("available")) == 1 ? "가능" : "예약됨")
                    .append(")  ");
            if (i++ % 10 == 0) s.append("\n");
        }
        return s.toString();
    }

    private Integer resolveIndexFromInput(String t, int max) {
        if (t == null) return null;
        String n = t.replaceAll("[^0-9]", "");
        if (n.isEmpty()) return null;
        int v = Integer.parseInt(n);
        return (v >= 1 && v <= max) ? v : null;
    }

    private List<String> parseSeats(String t) {
        if (t == null) return new ArrayList<>();
        String[] tokens = t.toUpperCase().split("[^A-Z0-9]+");
        List<String> out = new ArrayList<>();
        for (String k : tokens) if (k.matches("[A-Z][0-9]+")) out.add(k);
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : new HashMap<>();
    }

    private Integer toInt(Object v) {
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private Integer extractScheduleNum(Map<String, Object> m) {
        if (m == null) return null;
        Object v = m.get("scheduleNum");
        if (v == null) v = m.get("scheduleId");
        return toInt(v);
    }

    private Integer extractSeatCode(Map<String, Object> m) {
        if (m == null) return null;
        Object v = m.get("seatCode");
        if (v == null) v = m.get("seat_code");
        return toInt(v);
    }
}
