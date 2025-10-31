package com.hulahoop.blueback.ai.model.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * GeminiService (영화 단계형 + 자전거 조회형)
 */
@Service
public class GeminiService {

    private final RestTemplate restTemplate;
    private final IntentService intentService;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String baseUrl =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    private final List<Map<String, Object>> conversationHistory = new ArrayList<>();

    private enum Step { IDLE, BRANCH_SELECT, MOVIE_SELECT, SEAT_SELECT }
    private Step currentStep = Step.IDLE;

    private final Map<String, Object> bookingContext = new HashMap<>();
    private List<Map<String, Object>> lastCinemas = new ArrayList<>();
    private List<Map<String, Object>> lastMovies = new ArrayList<>();
    private List<Map<String, Object>> lastSeats = new ArrayList<>();

    public GeminiService(RestTemplate restTemplate, IntentService intentService) {
        this.restTemplate = restTemplate;
        this.intentService = intentService;
    }

    public synchronized String askGemini(String prompt) {

        conversationHistory.add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        // 취소 처리
        if (isCancelIntent(prompt)) {
            resetFlow();
            return "예매 흐름을 종료했습니다. 필요하시면 다시 말씀해 주세요 😊";
        }

        // ✅ 자전거는 영화 flow와 무관 — 바로 처리!
        String bikeResponse = handleBikeIntent(prompt);
        if (bikeResponse != null) return bikeResponse;

        // ✅ 영화 상태머신 동작
        String flowReply = handleMovieFlow(prompt);
        if (flowReply != null) return flowReply;

        return callGeminiFreeChat();
    }

    /* ----------------- 🎬 영화 단계형 흐름 ----------------- */
    private String handleMovieFlow(String userInput) {

        if (currentStep == Step.IDLE && isStartBookingIntent(userInput)) {
            Map<String, Object> result = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(result.get("cinemas"));

            lastCinemas = cinemas;
            currentStep = Step.BRANCH_SELECT;

            return formatCinemas(cinemas)
                    + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        if (currentStep == Step.BRANCH_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, lastCinemas.size());
            if (idx == null) return "지점 번호를 다시 입력해주세요. 예) 1번";

            String branchName = String.valueOf(lastCinemas.get(idx - 1).get("branch_name"));
            bookingContext.put("branchName", branchName);

            Map<String, Object> result = intentService.processIntent("movie_booking_step2",
                    Map.of("branchName", branchName));

            List<Map<String, Object>> movies = safeList(result.get("movies"));
            lastMovies = movies;
            currentStep = Step.MOVIE_SELECT;

            return "선택한 지점: " + branchName + "\n\n"
                    + formatMovies(movies)
                    + "\n예매할 영화 번호를 입력해주세요. 예) 2번";
        }

        if (currentStep == Step.MOVIE_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, lastMovies.size());
            if (idx == null) return "영화 번호를 다시 입력해주세요. 예) 2번";

            Map<String, Object> selected = lastMovies.get(idx - 1);
            Map<String, Object> movieCtx = new HashMap<>();
            movieCtx.put("index", idx);
            movieCtx.put("movieTitle", selected.get("movieTitle"));
            movieCtx.put("screeningDate", selected.get("screeningDate"));
            movieCtx.put("scheduleId", selected.get("scheduleId"));
            bookingContext.put("selectedMovie", movieCtx);

            Integer scheduleId = toInt(selected.get("scheduleId"));
            Map<String, Object> result = intentService.processIntent("movie_booking_step3",
                    Map.of("scheduleId", scheduleId));

            List<Map<String, Object>> seats = safeList(result.get("seats"));
            lastSeats = seats;
            currentStep = Step.SEAT_SELECT;

            return "선택한 영화: " + selected.get("movieTitle") + "\n"
                    + "상영일시: " + selected.get("screeningDate") + "\n\n"
                    + formatSeats(seats)
                    + "\n좌석번호를 입력해주세요. 예) A1, A2";
        }

        if (currentStep == Step.SEAT_SELECT) {
            List<String> requestedSeats = parseSeats(userInput);
            if (requestedSeats.isEmpty()) return "좌석 형식을 다시 입력해주세요. 예) A1, A2";

            List<Map<String, Object>> selectedSeats = new ArrayList<>();

            for (String token : requestedSeats) {
                Map<String, Object> seat = lastSeats.stream()
                        .filter(s -> token.equalsIgnoreCase(String.valueOf(s.get("seat"))))
                        .findFirst().orElse(null);

                if (seat == null) return token + " 좌석을 찾지 못했습니다.";
                if (toInt(seat.get("available")) != 1) return token + " 좌석은 예약이 불가합니다.";

                selectedSeats.add(seat);
            }

            Map<String, Object> movieCtx = safeMap(bookingContext.get("selectedMovie"));
            Integer scheduleId = toInt(movieCtx.get("scheduleId"));
            String memberName = "user01";

            for (Map<String, Object> seat : selectedSeats) {
                intentService.processIntent("movie_booking_step4",
                        Map.of("scheduleId", scheduleId,
                                "seatCode", seat.get("seat_code"),
                                "memberName", memberName));
            }

            resetFlow();
            return "✅ 좌석 예약이 완료되었습니다!\n10분 내 결제를 진행해주세요.";
        }

        return null;
    }

    /* ----------------- 🚲 자전거 즉시 조회 플로우 ----------------- */
    private String handleBikeIntent(String input) {
        String lower = input.toLowerCase();

        if (lower.contains("자전거") && (lower.contains("대여") || lower.contains("예약"))) {

            Map<String, Object> result = intentService.processIntent("bike_list", Map.of());
            List<Map<String, Object>> bikes = safeList(result.get("bicycles"));

            if (bikes.isEmpty()) return "🚲 대여 가능한 자전거가 없습니다.";

            StringBuilder sb = new StringBuilder("[🚲 대여 가능 자전거 목록]\n\n");
            int i = 1;
            for (Map<String, Object> b : bikes) {
                sb.append(i++).append(". 번호: ").append(b.get("bicycleCode")).append("\n")
                        .append("   종류: ").append(b.get("bicycleType")).append("\n")
                        .append("   상태: ").append(b.get("status")).append("\n\n");
            }
            return sb.toString().trim();
        }

        return null;
    }

    /* ----------------- 🔁 공통 함수 ----------------- */

    private boolean isStartBookingIntent(String input) {
        String lower = input.toLowerCase();
        return (lower.contains("영화") && lower.contains("예약")) || lower.contains("예매");
    }

    private boolean isCancelIntent(String input) {
        return input.contains("취소") || input.contains("그만") || input.contains("안할래");
    }

    private String formatCinemas(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[노바시네마 지점]\n\n");
        int i = 1;
        for (Map<String, Object> c : list) {
            sb.append(i++).append(". ").append(c.get("branch_name")).append("\n");
        }
        return sb.toString();
    }

    private String formatMovies(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[상영 영화 목록]\n\n");
        int i = 1;
        for (Map<String, Object> m : list) {
            sb.append(i++).append(". ").append(m.get("movieTitle")).append("\n")
                    .append("   시간: ").append(m.get("screeningDate")).append("\n\n");
        }
        return sb.toString();
    }

    private String formatSeats(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[좌석 현황]\n\n");
        int i = 1;
        for (Map<String, Object> s : list) {
            sb.append(s.get("seat")).append(" (")
                    .append(toInt(s.get("available")) == 1 ? "가능" : "예약됨")
                    .append(")  ");
            if (i++ % 10 == 0) sb.append("\n");
        }
        return sb.toString();
    }

    private Integer resolveIndexFromInput(String input, int max) {
        String onlyNum = input.replaceAll("[^0-9]", "");
        if (onlyNum.isEmpty()) return null;
        int n = Integer.parseInt(onlyNum);
        return (n >= 1 && n <= max) ? n : null;
    }

    private List<String> parseSeats(String input) {
        String[] tokens = input.toUpperCase().split("[^A-Z0-9]+");
        List<String> out = new ArrayList<>();
        for (String t : tokens) if (t.matches("[A-Z][0-9]+")) out.add(t);
        return out;
    }

    private List<Map<String, Object>> safeList(Object o) {
        if (o instanceof List) return (List<Map<String, Object>>) o;
        return new ArrayList<>();
    }

    private Map<String, Object> safeMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return new HashMap<>();
    }

    private Integer toInt(Object v) {
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (Exception e) { return null; }
    }

    private void resetFlow() {
        currentStep = Step.IDLE;
        bookingContext.clear();
        lastCinemas.clear();
        lastMovies.clear();
        lastSeats.clear();
    }

    private String callGeminiFreeChat() {
        Map<String, Object> req = Map.of("contents", conversationHistory);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = baseUrl + "?key=" + apiKey;

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(req, headers), Map.class);

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) Objects.requireNonNull(response.getBody()).get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            String text = ((List<Map<String, String>>) content.get("parts")).get(0).get("text");

            conversationHistory.add(Map.of("role", "model", "parts", List.of(Map.of("text", text))));
            return text;
        } catch (Exception e) {
            return "AI 호출 중 오류: " + e.getMessage();
        }
    }

    public void resetConversation() {
        conversationHistory.clear();
    }
}
