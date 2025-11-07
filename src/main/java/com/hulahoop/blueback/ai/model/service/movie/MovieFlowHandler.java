package com.hulahoop.blueback.ai.model.service.movie;

import com.hulahoop.blueback.ai.model.service.IntentService;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MovieFlowHandler {

    private final IntentService intentService;

    public MovieFlowHandler(IntentService intentService) {
        this.intentService = intentService;
    }

    /** 🎬 영화 예매 플로우 처리 */
    public String handleMovieFlow(String userInput, UserSession s, String userId) {

        // 1️⃣ 시작: "영화 예약할래" 등
        if (s.getStep() == UserSession.Step.IDLE && isStartBookingIntent(userInput)) {
            Map<String, Object> res = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(res.get("cinemas"));
            s.setLastCinemas(cinemas);
            s.setStep(UserSession.Step.BRANCH_SELECT);

            return formatCinemas(cinemas) + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        // 2️⃣ 지점 선택
        if (s.getStep() == UserSession.Step.BRANCH_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, s.getLastCinemas().size());
            if (idx == null) return "⚠️ 지점 번호를 다시 입력해주세요. 예) 1번";

            String branchName = String.valueOf(s.getLastCinemas().get(idx - 1).get("branch_name"));
            s.getBookingContext().put("branchName", branchName);

            Map<String, Object> res = intentService.processIntent("movie_booking_step2", Map.of("branchName", branchName));
            List<Map<String, Object>> movies = safeList(res.get("movies"));
            s.setLastMovies(movies);
            s.setStep(UserSession.Step.MOVIE_SELECT);

            return "🎬 선택 지점: " + branchName
                    + "\n\n" + formatMovies(movies)
                    + "\n예매할 영화 번호를 입력해주세요. 예) 2번";
        }

        // 3️⃣ 영화 선택
        if (s.getStep() == UserSession.Step.MOVIE_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, s.getLastMovies().size());
            if (idx == null) return "⚠️ 영화 번호를 다시 입력해주세요. 예) 2번";

            Map<String, Object> selected = s.getLastMovies().get(idx - 1);
            Integer scheduleNum = extractScheduleNum(selected);
            if (scheduleNum == null) return "회차 번호 오류";

            s.getBookingContext().put("selectedMovie", selected);
            s.getBookingContext().put("scheduleNum", scheduleNum);

            Map<String, Object> res = intentService.processIntent("movie_booking_step3", Map.of("scheduleNum", scheduleNum));
            List<Map<String, Object>> seats = safeList(res.get("seats"));
            s.setLastSeats(seats);
            s.setStep(UserSession.Step.SEAT_SELECT);

            return "🎞️ " + selected.get("movieTitle") + " 좌석 현황입니다.\n"
                    + formatSeats(seats)
                    + "\n좌석을 입력해주세요 (예: A1)\n또는 '상세 좌석 보기'를 입력하세요.\n"
                    + "\n<!-- scheduleNum:" + scheduleNum + " -->";
        }

        // 4️⃣ 좌석 선택
        if (s.getStep() == UserSession.Step.SEAT_SELECT) {
            String input = userInput.trim();

            // ✅ 상세 좌석 보기 (모달 트리거)
            if (input.contains("상세")) {
                Integer scheduleNum = (Integer) s.getBookingContext().get("scheduleNum");
                return "🎬 좌석 선택창을 열게요!\n\n<!-- scheduleNum:" + scheduleNum + " -->";
            }

            // ✅ A1, A2 형식 입력
            List<Map<String, Object>> seats = s.getLastSeats();
            Map<String, Object> movieCtx = s.getBookingContext();
            String[] tokens = input.split("[,\\s]+");
            List<Map<String, Object>> selectedSeats = new ArrayList<>();

            for (String t : tokens) {
                String seatName = t.trim().toUpperCase();
                Map<String, Object> seat = seats.stream()
                        .filter(x -> (x.get("row_label") + "" + x.get("col_num"))
                                .equalsIgnoreCase(seatName))
                        .findFirst()
                        .orElse(null);

                if (seat == null) return "❌ 좌석 " + seatName + " 을(를) 찾을 수 없습니다.";

                boolean reserved = "TRUE".equalsIgnoreCase(String.valueOf(seat.get("reserved"))) ||
                        "1".equals(String.valueOf(seat.get("reserved")));
                if (reserved) return "❌ " + seatName + " 은(는) 이미 예약된 좌석입니다.";

                selectedSeats.add(seat);
            }

            // ✅ 예약 Intent 호출
            Integer scheduleNum = (Integer) movieCtx.get("scheduleNum");
            for (Map<String, Object> seat : selectedSeats) {
                Integer seatCode = extractSeatCode(seat);
                intentService.processIntent("movie_booking_step4",
                        Map.of("scheduleNum", scheduleNum, "seatCode", seatCode));
            }

            s.reset();
            return "✅ 좌석 예약 완료!\n💳 10분 내 결제를 진행해주세요!";
        }

        return null;
    }

    // ────────────────────────────── 유틸 ──────────────────────────────

    private boolean isStartBookingIntent(String t) {
        t = (t == null ? "" : t.toLowerCase());
        return (t.contains("영화") && t.contains("예약")) || t.contains("예매");
    }

    private Integer resolveIndexFromInput(String t, int max) {
        if (t == null) return null;
        String n = t.replaceAll("[^0-9]", "");
        if (n.isEmpty()) return null;
        int v = Integer.parseInt(n);
        return (v >= 1 && v <= max) ? v : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : new ArrayList<>();
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

    private Integer toInt(Object v) {
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    // ────────────────────────────── 출력 포맷 ──────────────────────────────

    private String formatCinemas(List<Map<String, Object>> l) {
        StringBuilder s = new StringBuilder("📍 가까운 영화관 목록\n\n");
        int i = 1;
        for (Map<String, Object> c : l)
            s.append(i++).append(") ").append(c.get("branch_name"))
                    .append(" - ").append(c.get("address")).append("\n");
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

    /** 🎟️ 좌석 이모지 포맷 */
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
                        "TRUE".equalsIgnoreCase(String.valueOf(seat.get("reserved"))) ||
                                "1".equals(String.valueOf(seat.get("reserved")));

                if (isAisle == 1) {
                    sb.append("   "); // 통로
                } else {
                    sb.append(reserved ? "🟥" : "🟩").append(" ");
                }
            }
            sb.append("\n");
        }

        sb.append("\n🟩 가능 / 🟥 예약됨\n");
        if (!aisleCols.isEmpty()) {
            sb.append("*").append(String.join(",", aisleCols.stream().map(String::valueOf).toList()))
                    .append("열은 통로입니다.\n");
        }
        sb.append("좌석 입력 예시: A2\n");
        return sb.toString();
    }
}