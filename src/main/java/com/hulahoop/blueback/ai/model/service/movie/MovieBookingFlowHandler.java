package com.hulahoop.blueback.ai.model.service.movie;

import com.hulahoop.blueback.ai.model.service.IntentService;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MovieBookingFlowHandler {

    private final IntentService intentService;
    private final MovieFormatter formatter;
    private final UserMapper userMapper;

    public MovieBookingFlowHandler(IntentService intentService, MovieFormatter formatter, UserMapper userMapper) {
        this.intentService = intentService;
        this.formatter = formatter;
        this.userMapper = userMapper;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object obj) {
        return (obj instanceof List) ? (List<Map<String, Object>>) obj : new ArrayList<>();
    }

    private boolean containsAny(String text, List<String> words) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return words.stream().anyMatch(lower::contains);
    }

    private Integer resolveIndexFromInput(String input, int maxSize) {
        if (input == null) return null;
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        int v = Integer.parseInt(digits);
        return (v >= 1 && v <= maxSize) ? v : null;
    }

    private Map<String, Object> findSeatByLabel(List<Map<String, Object>> seats, String label) {
        if (label.length() < 2) return null;
        String row = label.substring(0, 1);
        String col = label.substring(1);

        for (Map<String, Object> seat : seats) {
            if (row.equalsIgnoreCase(String.valueOf(seat.get("row_label"))) &&
                    col.equals(String.valueOf(seat.get("col_num")))) {
                return seat;
            }
        }
        return null;
    }

    private String checkGlobalCommands(String userInput, UserSession s) {
        String lower = userInput.toLowerCase();

        if (containsAny(lower, List.of("그만", "종료", "취소", "나가기", "닫기", "안할래"))) {
            s.reset();
            return "네, 알겠습니다. 필요하실 때 언제든 불러주세요 😊";
        }

        if (containsAny(lower, List.of("조회", "예매함", "예약함", "내역"))) {
            s.reset();
            return "📄 예매 내역 조회 기능으로 이동합니다. 무엇을 조회할까요?";
        }

        if (containsAny(lower, List.of("자전거", "바이크"))) {
            s.reset();
            return "🚲 자전거 예약 기능으로 이동합니다. 무엇을 도와드릴까요?";
        }

        return null;
    }

    private String extractDateFilter(String userInput) {
        String lower = userInput.toLowerCase();

        if (lower.contains("오늘")) return "today";
        if (lower.contains("내일")) return "tomorrow";

        // ex: "11월 20일", "11월20일"
        if (lower.matches(".*\\d{1,2}월\\s*\\d{1,2}일.*")) {
            String month = lower.replaceAll(".*?(\\d{1,2})월.*", "$1");
            String day = lower.replaceAll(".*?(\\d{1,2})일.*", "$1");
            return "2025-" + month + "-" + day;
        }

        return null;
    }

    public String handle(String userInput, UserSession s, String userId) {

        if (userInput == null) return "입력을 다시 말씀해주세요.";

        String normalized = userInput.trim().toLowerCase();

        // 🔥 모든 단계에서 글로벌 명령어 먼저 체크
        String global = checkGlobalCommands(normalized, s);
        if (global != null) return global;

        // ==============================
        // STEP 1: 예매 시작 → 지점 목록 출력
        // ==============================
        if (s.getStep() == UserSession.Step.IDLE) {

            Map<String, Object> res = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(res.get("cinemas"));

            s.setLastCinemas(cinemas);
            s.setStep(UserSession.Step.BRANCH_SELECT);

            return formatter.formatCinemas(cinemas)
                    + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        // ==============================
        // STEP 2: 지점 선택
        // ==============================
        if (s.getStep() == UserSession.Step.BRANCH_SELECT) {

            // 날짜 필터 입력했으면 저장
            String dateFilter = extractDateFilter(userInput);
            if (dateFilter != null) s.getBookingContext().put("dateFilter", dateFilter);

            Integer idx = resolveIndexFromInput(userInput, s.getLastCinemas().size());
            if (idx == null) {
                return "지점 번호를 다시 입력해주세요.\n\n"
                        + "또는 다른 기능을 원하시면 말해주세요.\n예시: \"예매 조회\", \"자전거 예약\"";
            }

            Map<String, Object> selected = s.getLastCinemas().get(idx - 1);
            String branchNum = String.valueOf(selected.get("branch_num"));
            String branchName = String.valueOf(selected.get("branch_name"));

            s.getBookingContext().put("branchNum", branchNum);
            s.getBookingContext().put("branchName", branchName);

            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step2",
                    Map.of(
                            "branchNum", branchNum,
                            "dateFilter", s.getBookingContext().getOrDefault("dateFilter", "today")
                    )
            );

            List<Map<String, Object>> schedules = safeList(res.get("movies"));
            s.setLastMovies(schedules);
            s.setStep(UserSession.Step.MOVIE_SELECT);

            return "🎬 지점을 선택했습니다!\n"
                    + "지점: " + branchName + "\n\n"
                    + formatter.formatSchedules(schedules)
                    + "\n예매할 스케줄 번호를 입력해주세요. 예) 2번";
        }

        // ==============================
        // STEP 3: 스케줄 선택
        // ==============================
        if (s.getStep() == UserSession.Step.MOVIE_SELECT) {

            Integer idx = resolveIndexFromInput(userInput, s.getLastMovies().size());
            if (idx == null) {
                return "스케줄 번호를 다시 입력해주세요.\n또는 \"예매 조회\", \"자전거\" 같은 다른 기능을 말해주세요.";
            }

            Map<String, Object> sel = s.getLastMovies().get(idx - 1);

            s.getBookingContext().put("scheduleNum", String.valueOf(sel.get("scheduleNum")));
            s.getBookingContext().put("movieTitle", String.valueOf(sel.get("movieTitle")));

            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step3",
                    Map.of("scheduleNum", sel.get("scheduleNum"))
            );

            List<Map<String, Object>> seats = safeList(res.get("seats"));
            s.setLastSeats(seats);
            s.setStep(UserSession.Step.SEAT_SELECT);

            return "🎥 선택한 영화: " + sel.get("movieTitle") + "\n\n"
                    + formatter.formatSeats(seats)
                    + "\n원하시는 좌석을 입력해주세요. 예) A3"
                    + "\n\n<!-- scheduleNum:" + sel.get("scheduleNum") + " -->";
        }

        // ==============================
        // STEP 4: 좌석 선택
        // ==============================
        if (s.getStep() == UserSession.Step.SEAT_SELECT) {

            String seatInput = userInput.trim().toUpperCase();
            String scheduleNum = String.valueOf(s.getBookingContext().get("scheduleNum"));

            MemberDTO member = userMapper.findById(userId);
            if (member == null) return "회원 정보를 찾을 수 없습니다.";

            String phoneNumber = member.getPhoneNum();

            Map<String, Object> seat = findSeatByLabel(s.getLastSeats(), seatInput);
            if (seat == null) return "해당 좌석을 찾을 수 없습니다. 다시 입력해주세요.";

            if (Boolean.parseBoolean(String.valueOf(seat.get("reserved")))) {
                return "❌ 이미 예약된 좌석입니다. 다른 좌석을 선택해주세요.";
            }

            int seatCode = Integer.parseInt(String.valueOf(seat.get("seat_code")));

            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step4",
                    Map.of(
                            "scheduleNum", scheduleNum,
                            "seatCode", seatCode,
                            "phoneNumber", phoneNumber
                    )
            );

            if (res.containsKey("message")) {
                s.reset();
                return "🎉 예매가 완료되었습니다!\n\n"
                        + "다음 작업을 선택해주세요:\n"
                        + "1️⃣ 내 예매 내역 확인\n"
                        + "2️⃣ 예매 취소하기\n"
                        + "3️⃣ 다른 영화 예매하기\n"
                        + "4️⃣ 종료하기";
            }

            return "❌ 예매 실패: " + res.getOrDefault("error", "알 수 없는 오류");
        }

        return "처리할 수 없는 상태입니다. 다시 시도해주세요.";
    }
}
