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

    private final String gatewayNotifyUrl = "http://localhost:8080/internal/seat-updated";

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

    public synchronized String askGemini(String prompt, String userId) {
        if (userId == null || userId.isBlank()) userId = "guest";

        userSessions.putIfAbsent(userId, new UserSession());
        UserSession session = userSessions.get(userId);

        session.history.add(Map.of("role","user","parts", List.of(Map.of("text", prompt))));

        if (isCancelIntent(prompt)) {
            resetFlow(session);
            return "✅ 예약이 취소되었습니다. 다른 도움이 필요하신가요?";
        }

        String bikeRes = handleBikeIntent(prompt);
        if (bikeRes != null) return bikeRes;

        String movieReply = handleMovieFlow(prompt, session, userId);
        if (movieReply != null) return movieReply;

        return callGeminiFreeChat(session.history);
    }

    private String handleMovieFlow(String userInput, UserSession s, String userId) {

        // 시작
        if (s.step == Step.IDLE && isStartBookingIntent(userInput)) {
            Map<String, Object> res = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(res.get("cinemas"));

            s.lastCinemas = cinemas;
            s.step = Step.BRANCH_SELECT;

            return formatCinemas(cinemas)
                    + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        // 지점 선택
        if (s.step == Step.BRANCH_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, s.lastCinemas.size());
            if (idx == null) return "⚠️ 지점 번호를 다시 입력해주세요. 예) 1번";

            String branchName = String.valueOf(s.lastCinemas.get(idx - 1).get("branch_name"));
            s.bookingContext.put("branchName", branchName);

            Map<String, Object> res = intentService.processIntent("movie_booking_step2", Map.of("branchName", branchName));
            List<Map<String, Object>> movies = safeList(res.get("movies"));

            s.lastMovies = movies;
            s.step = Step.MOVIE_SELECT;

            return "🎬 선택한 지점: " + branchName
                    + "\n\n" + formatMovies(movies)
                    + "\n예매할 영화 번호를 입력해주세요. 예) 2번";
        }

        // 영화 선택
        if (s.step == Step.MOVIE_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, s.lastMovies.size());
            if (idx == null) return "⚠️ 영화 번호를 다시 입력해주세요. 예) 2번";

            Map<String, Object> selected = s.lastMovies.get(idx - 1);
            Integer scheduleNum = extractScheduleNum(selected);
            if (scheduleNum == null) return "회차 번호 오류";

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("movieTitle", selected.get("movieTitle"));
            ctx.put("screeningDate", selected.get("screeningDate"));
            ctx.put("scheduleNum", scheduleNum);
            ctx.put("screeningNumber", selected.get("screeningNumber"));
            s.bookingContext.put("selectedMovie", ctx);

            Map<String, Object> res = intentService.processIntent("movie_booking_step3", Map.of("scheduleNum", scheduleNum));
            s.lastSeats = safeList(res.get("seats"));
            s.step = Step.SEAT_SELECT;

            return "🎞 선택 영화: " + selected.get("movieTitle")
                    + "\n상영일시: " + selected.get("screeningDate")
                    + "\n\n" + formatSeats(s.lastSeats)
                    + "\n좌석을 입력해주세요 (예: A1)"
                    + "\n또는 '상세 좌석 보기'를 입력하세요.\n\n[상세 좌석 보기]"
                    + "\n<!-- scheduleNum:" + scheduleNum + " -->";

        }

        // 좌석 선택 단계
        if (s.step == Step.SEAT_SELECT) {

            // ✅ 상세 좌석 보기 명령 — UI만 오픈 (이미 해결하셨다고 하셔서 문구만 유지)
            if (userInput != null && userInput.contains("상세")) {
                return "🎬 좌석 선택창을 열게요!";
            }

            // ✅ 좌석 번호 입력
            List<String> reqSeats = parseSeats(userInput);
            if (reqSeats.isEmpty()) {
                return "⚠️ 좌석 형식 오류. 예) A1, A2\n또는 '상세 좌석 보기'";
            }

            // ✅ 통로 열 집합 동적 계산 (예: 3,4,9,10 등)
            Set<Integer> aisleCols = computeAisleCols(s.lastSeats);

            // ✅ 먼저: 사용자가 고른 좌석 중 통로 열 포함 여부 사전 차단
            for (String seatStr : reqSeats) {
                Integer col = extractColNum(seatStr); // A12 -> 12
                if (col != null && aisleCols.contains(col)) {
                    String cols = String.join(",", aisleCols.stream().map(String::valueOf).toList());
                    return "❌ " + seatStr + "는 통로 열입니다.\n"
                            + "통로(" + cols + "열)는 예약할 수 없습니다. 다른 좌석을 선택해주세요.";
                }
            }

            Map<String, Object> movieCtx = safeMap(s.bookingContext.get("selectedMovie"));
            Integer scheduleNum = toInt(movieCtx.get("scheduleNum"));

            // ✅ 좌석 존재/예약/통로 여부 최종 검증 (이중 방어)
            for (String seatStr : reqSeats) {
                Map<String, Object> seat = s.lastSeats.stream()
                        .filter(x -> (x.get("row_label") + "" + x.get("col_num")).equalsIgnoreCase(seatStr))
                        .findFirst().orElse(null);

                if (seat == null) return "❌ " + seatStr + " 좌석 없음";

                // 통로이면 거절
                int isAisle = toInt(seat.get("is_aisle")) != null ? toInt(seat.get("is_aisle")) : 0;
                if (isAisle == 1) {
                    String cols = String.join(",", aisleCols.stream().map(String::valueOf).toList());
                    return "❌ " + seatStr + "는 통로입니다. 통로(" + cols + "열)는 예약 불가입니다.";
                }

                // 예약 여부
                boolean reserved = "TRUE".equalsIgnoreCase(String.valueOf(seat.get("reserved")))
                        || "1".equals(String.valueOf(seat.get("reserved")));
                if (reserved) return "❌ " + seatStr + " 예약 불가 (이미 예약됨)";

                // 좌석코드 유효성
                Integer seatCode = extractSeatCode(seat);
                if (seatCode == null) return "❌ " + seatStr + " 좌석 코드가 유효하지 않습니다.";

                // 실제 예약 처리
                intentService.processIntent("movie_booking_step4",
                        Map.of("scheduleNum", scheduleNum, "seatCode", seatCode));
            }

            resetFlow(s);
            return "✅ 좌석 예약 완료!\n💳 10분 내 결제를 진행해주세요!";
        }

        return null;
    }

    public String completeSeatSelection(String userId) {
        if (userId == null || userId.isBlank()) return null;

        UserSession session = userSessions.get(userId);
        if (session == null) return null;

        resetFlow(session);

        return "✅ 좌석 선택이 완료되었습니다!\n💳 10분 내 결제해주세요.";
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
            history.add(Map.of("role","model","parts", List.of(Map.of("text", text))));
            return text;

        } catch (Exception e) {
            return "AI 호출 오류: " + e.getMessage();
        }
    }

    private void resetFlow(UserSession s) {
        s.step = Step.IDLE;
        s.bookingContext.clear();
        s.lastCinemas.clear();
        s.lastMovies.clear();
        s.lastSeats.clear();
        s.history.clear();
    }

    public void resetConversation(String userId) {
        userSessions.remove(userId);
    }

    // ───────── Utility Methods ─────────
    private boolean isStartBookingIntent(String t) {
        t = (t == null ? "" : t.toLowerCase());
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
        for (Map<String, Object> m : l) {
            s.append(i++).append(". ").append(m.get("movieTitle"))
                    .append("\n   상영관: ").append(m.get("screeningNumber")).append("관")
                    .append("\n   시간: ").append(m.get("screeningDate")).append("\n\n");
        }
        return s.toString();
    }

    private String formatSeats(List<Map<String, Object>> seats) {
        if (seats == null || seats.isEmpty()) return "좌석 정보가 없습니다.";

        StringBuilder sb = new StringBuilder();
        Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
        Set<Integer> aisleCols = new TreeSet<>();

        for (Map<String, Object> seat : seats) {
            String row = String.valueOf(seat.get("row_label"));
            rows.putIfAbsent(row, new ArrayList<>());
            rows.get(row).add(seat);

            int isAisle = Integer.parseInt(String.valueOf(seat.get("is_aisle")));
            if (isAisle == 1) {
                aisleCols.add(Integer.parseInt(String.valueOf(seat.get("col_num"))));
            }
        }

        rows.values().forEach(r ->
                r.sort(Comparator.comparingInt(s -> Integer.parseInt(String.valueOf(s.get("col_num")))))
        );

        for (String row : rows.keySet()) {
            sb.append(row).append(" | ");

            for (Map<String, Object> seat : rows.get(row)) {
                int isAisle = Integer.parseInt(String.valueOf(seat.get("is_aisle")));
                boolean reserved =
                        "TRUE".equalsIgnoreCase(String.valueOf(seat.get("reserved")))
                                || "1".equals(String.valueOf(seat.get("reserved")));

                if (isAisle == 1) {
                    sb.append("  ");
                } else {
                    sb.append(reserved ? "🟥" : "🟩").append(" ");
                }
            }
            sb.append("\n");
        }

        sb.append("🟩 가능 / 🟥 예약됨\n");

        if (!aisleCols.isEmpty()) {
            sb.append("*").append(String.join(",", aisleCols.stream().map(String::valueOf).toList()))
                    .append("열은 통로입니다.\n");
        }

        sb.append("좌석 입력 예시: A2\n");
        return sb.toString();
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
        Object v = m.get("scheduleNum");
        if (v == null) v = m.get("scheduleId");
        return toInt(v);
    }

    private Integer extractSeatCode(Map<String, Object> m) {
        Object v = m.get("seatCode");
        if (v == null) v = m.get("seat_code");
        return toInt(v);
    }

    // ===== 추가 유틸 =====

    // 통로 열 집합 생성 (formatSeats 로직과 동일한 기준)
    private Set<Integer> computeAisleCols(List<Map<String, Object>> seats) {
        Set<Integer> aisleCols = new TreeSet<>();
        if (seats == null) return aisleCols;
        for (Map<String, Object> seat : seats) {
            Integer isAisle = toInt(seat.get("is_aisle"));
            Integer col = toInt(seat.get("col_num"));
            if (isAisle != null && isAisle == 1 && col != null) {
                aisleCols.add(col);
            }
        }
        return aisleCols;
    }

    // "A12" -> 12
    private Integer extractColNum(String seatStr) {
        if (seatStr == null) return null;
        try {
            String num = seatStr.replaceAll("^[A-Z]+", "");
            return num.isEmpty() ? null : Integer.parseInt(num);
        } catch (Exception e) {
            return null;
        }
    }
}
