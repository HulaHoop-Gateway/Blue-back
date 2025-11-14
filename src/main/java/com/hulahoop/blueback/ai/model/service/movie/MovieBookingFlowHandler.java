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

        String normalized = userInput.trim().toLowerCase();

        // ✅ 전역 종료/취소 키워드 감지 (모든 단계에서 동작)
        if (containsAny(normalized, List.of("그만", "취소", "안할래", "종료", "나가기", "닫기", "안돼"))) {
            s.reset(); // 세션 초기화
            return "네, 알겠습니다. 필요하신 게 있으면 말씀해주세요 😊";
        }

        // 1️⃣ 예매 흐름 시작
        if (s.getStep() == UserSession.Step.IDLE) {
            Map<String, Object> res = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(res.get("cinemas"));
            s.setLastCinemas(cinemas);
            s.setStep(UserSession.Step.BRANCH_SELECT);

            return formatter.formatCinemas(cinemas)
                    + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        // 2️⃣ 지점 선택
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

        // 3️⃣ 스케줄 선택
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
                    + "\n상세 좌석도 확인할 수 있습니다. 예시 : 상세 좌석, 상세좌석 보기"
                    + "\n\n<!-- scheduleNum:" + scheduleNum + " -->";
        }

        // 좌석 선택
        if (s.getStep() == UserSession.Step.SEAT_SELECT) {
            String seatInput = userInput.trim().toUpperCase();
            String scheduleNum = String.valueOf(s.getBookingContext().get("scheduleNum"));

            // 좌석 보기 명령
            if (seatInput.contains("상세") || seatInput.contains("좌석 보여")
                    || seatInput.contains("좌석 볼래") || seatInput.contains("좌석 보기")) {
                return "🎬 좌석 선택창을 열게요!\n\n<!-- scheduleNum:" + scheduleNum + " -->";
            }

            // 정상 좌석 선택
            MemberDTO member = userMapper.findById(userId);
            if (member == null) return "❌ 회원 정보를 찾을 수 없습니다. 로그인 상태를 확인해주세요.";

            String phoneNumber = member.getPhoneNum();
            Map<String, Object> selectedSeat = findSeatByLabel(s.getLastSeats(), seatInput);
            if (selectedSeat == null) return "❌ 해당 좌석을 찾을 수 없습니다. 다시 입력해주세요.";

            // 🔥 여기 추가해야 함: 예약된 좌석 체크
            boolean reserved = Boolean.parseBoolean(String.valueOf(selectedSeat.get("reserved")));
            if (reserved) {
                return "❌ 해당 좌석은 이미 예약되었습니다. 다른 좌석을 선택해주세요.";
            }

            Object seatCodeObj = selectedSeat.get("seat_code");
            if (seatCodeObj == null) return "❌ 좌석 코드 정보가 누락되어 예매할 수 없습니다. 관리자에게 문의해주세요.";

            int seatCode = Integer.parseInt(String.valueOf(seatCodeObj));

            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step4",
                    Map.of(
                            "scheduleNum", scheduleNum,
                            "seatCode", seatCode,
                            "phoneNumber", phoneNumber
                    )
            );

            if (res.containsKey("message")) {
                s.setStep(UserSession.Step.IDLE);
                return "🎉 예매가 완료되었습니다!\n\n"
                        + "다음 중 원하시는 작업을 선택해주세요:\n"
                        + "1️⃣ 내 예매 내역 확인\n"
                        + "2️⃣ 예매 취소하기\n"
                        + "3️⃣ 다른 영화 예매하기\n"
                        + "4️⃣ 종료하기\n"
                        + "예: \"1번\" 또는 \"내 예매 확인\"";
            } else {
                return "❌ 예매 실패: " + res.getOrDefault("error", "알 수 없는 오류");
            }
        }

        return null;
    }

    /** 입력에서 숫자 인덱스 추출 */
    private Integer resolveIndexFromInput(String t, int max) {
        if (t == null) return null;
        String n = t.replaceAll("[^0-9]", "");
        if (n.isEmpty()) return null;
        int v = Integer.parseInt(n);
        return (v >= 1 && v <= max) ? v : null;
    }

    /** 좌석 라벨(A3 등)으로 좌석 정보 찾기 */
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

    /** 간단 리스트 변환 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : new ArrayList<>();
    }

    /** 문자열 포함 체크 (대소문자 무시) */
    private boolean containsAny(String text, List<String> keywords) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }
}
