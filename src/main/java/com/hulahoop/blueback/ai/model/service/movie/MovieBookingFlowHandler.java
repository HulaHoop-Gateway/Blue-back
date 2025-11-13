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

    public String handle(String userInput, UserSession s, String userId) {
        // 예매 흐름 시작
        if (s.getStep() == UserSession.Step.IDLE) {
            Map<String, Object> res = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(res.get("cinemas"));
            s.setLastCinemas(cinemas);
            s.setStep(UserSession.Step.BRANCH_SELECT);

            return formatter.formatCinemas(cinemas) + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        if (s.getStep() == UserSession.Step.BRANCH_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, s.getLastCinemas().size());
            if (idx == null) return "⚠️ 지점 번호를 다시 입력해주세요. 예) 1번";

            Map<String, Object> selectedCinema = s.getLastCinemas().get(idx - 1);
            String branchNum = String.valueOf(selectedCinema.get("branch_num"));
            String branchName = String.valueOf(selectedCinema.get("branch_name"));

            s.getBookingContext().put("branchNum", branchNum);
            s.getBookingContext().put("branchName", branchName);

            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step2",
                    Map.of("branchNum", branchNum)
            );
            List<Map<String, Object>> schedules = safeList(res.get("movies"));
            s.setLastMovies(schedules);

            s.setStep(UserSession.Step.MOVIE_SELECT);

            return "✅ 지점이 선택되었습니다!\n"
                    + "지점 코드: " + branchNum + "\n"
                    + "지점 이름: " + branchName + "\n\n"
                    + formatter.formatSchedules(schedules)
                    + "\n예매할 스케줄 번호를 입력해주세요. 예) 2번";
        }

        if (s.getStep() == UserSession.Step.MOVIE_SELECT) {
            Integer idx = resolveIndexFromInput(userInput, s.getLastMovies().size());
            if (idx == null) return "⚠️ 스케줄 번호를 다시 입력해주세요. 예) 2번";

            Map<String, Object> selectedSchedule = s.getLastMovies().get(idx - 1);
            String scheduleNum = String.valueOf(selectedSchedule.get("scheduleNum"));
            String movieTitle = String.valueOf(selectedSchedule.get("movieTitle"));

            s.getBookingContext().put("scheduleNum", scheduleNum);
            s.getBookingContext().put("movieTitle", movieTitle);

            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step3",
                    Map.of("scheduleNum", scheduleNum)
            );
            List<Map<String, Object>> seats = safeList(res.get("seats"));
            s.setLastSeats(seats);

            s.setStep(UserSession.Step.SEAT_SELECT);

            return "🎟️ 선택한 영화: " + movieTitle + "\n\n"
                    + formatter.formatSeats(seats)
                    + "\n원하시는 좌석을 입력해주세요. 예) A3";
        }

        if (s.getStep() == UserSession.Step.SEAT_SELECT) {
            String seatInput = userInput.trim().toUpperCase();
            String scheduleNum = String.valueOf(s.getBookingContext().get("scheduleNum"));

            MemberDTO member = userMapper.findById(userId);
            if (member == null) return "❌ 회원 정보를 찾을 수 없습니다. 로그인 상태를 확인해주세요.";
            String memberCode = member.getMemberCode();

            Map<String, Object> selectedSeat = findSeatByLabel(s.getLastSeats(), seatInput);
            if (selectedSeat == null) return "❌ 해당 좌석을 찾을 수 없습니다. 다시 입력해주세요.";

            Object seatCodeObj = selectedSeat.get("seat_code");
            if (seatCodeObj == null) return "❌ 좌석 코드 정보가 누락되어 예매할 수 없습니다. 관리자에게 문의해주세요.";

            int seatCode = Integer.parseInt(String.valueOf(seatCodeObj));

            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step4",
                    Map.of(
                            "scheduleNum", scheduleNum,
                            "seatCode", seatCode,
                            "memberCode", memberCode
                    )
            );

            if (res.containsKey("message")) {
                s.setStep(UserSession.Step.IDLE);
                return res.get("message").toString()
                        + "\n\n다음 중 원하시는 작업을 선택해주세요:\n"
                        + "1️⃣ 내 예매 내역 확인\n"
                        + "2️⃣ 예매 취소하기\n"
                        + "3️⃣ 다른 영화 예매하기\n"
                        + "4️⃣ 종료하기\n\n"
                        + "예: \"1번\" 또는 \"내 예매 확인\"";
            } else {
                return "❌ 예매 실패: " + res.getOrDefault("error", "알 수 없는 오류");
            }
        }

        return null;
    }

    private Integer resolveIndexFromInput(String t, int max) {
        if (t == null) return null;
        String n = t.replaceAll("[^0-9]", "");
        if (n.isEmpty()) return null;
        int v = Integer.parseInt(n);
        return (v >= 1 && v <= max) ? v : null;
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : new ArrayList<>();
    }
}
