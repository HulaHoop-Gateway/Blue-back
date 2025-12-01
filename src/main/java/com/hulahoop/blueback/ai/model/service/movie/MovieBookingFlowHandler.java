package com.hulahoop.blueback.ai.model.service.movie;

import com.hulahoop.blueback.ai.model.service.IntentService;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import com.hulahoop.blueback.email.model.service.EmailService;
import com.hulahoop.blueback.kakao.model.service.KakaoLocalService;
import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MovieBookingFlowHandler {

    private final IntentService intentService;
    private final MovieFormatter formatter;
    private final UserMapper userMapper;
    private final KakaoLocalService kakaoLocalService;
    private final EmailService emailService;

    public MovieBookingFlowHandler(
            IntentService intentService,
            MovieFormatter formatter,
            UserMapper userMapper,
            KakaoLocalService kakaoLocalService,
            EmailService emailService) {
        this.intentService = intentService;
        this.formatter = formatter;
        this.userMapper = userMapper;
        this.kakaoLocalService = kakaoLocalService;
        this.emailService = emailService;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object obj) {
        return (obj instanceof List) ? (List<Map<String, Object>>) obj : new ArrayList<>();
    }

    private boolean containsAny(String text, List<String> words) {
        if (text == null)
            return false;
        String lower = text.toLowerCase();
        return words.stream().anyMatch(lower::contains);
    }

    private Integer resolveIndexFromInput(String input, int maxSize) {
        if (input == null)
            return null;
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.isEmpty())
            return null;
        int v = Integer.parseInt(digits);
        return (v >= 1 && v <= maxSize) ? v : null;
    }

    private Map<String, Object> findSeatByLabel(List<Map<String, Object>> seats, String label) {
        if (label.length() < 2)
            return null;
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

        if (lower.contains("오늘"))
            return "today";
        if (lower.contains("내일"))
            return "tomorrow";

        if (lower.matches(".*\\d{1,2}월\\s*\\d{1,2}일.*")) {
            String month = lower.replaceAll(".*?(\\d{1,2})월.*", "$1");
            String day = lower.replaceAll(".*?(\\d{1,2})일.*", "$1");
            return "2025-" + month + "-" + day;
        }

        return null;
    }

    public String handle(String userInput, UserSession s, String userId) {

        if (userInput == null)
            return "입력을 다시 말씀해주세요.";

        String normalized = userInput.trim().toLowerCase();

        String global = checkGlobalCommands(normalized, s);
        if (global != null)
            return global;

        // ------------------------------------------------
        // STEP 1: 예매 시작 → 영화관 목록 + 거리순 정렬
        // ------------------------------------------------
        if (s.getStep() == UserSession.Step.IDLE) {

            // 날짜 필터 저장
            String dateFilter = extractDateFilter(userInput);
            if (dateFilter != null) {
                s.getBookingContext().put("dateFilter", dateFilter);
            } else {
                s.getBookingContext().putIfAbsent("dateFilter", "today");
            }

            // 사용자 정보 조회
            MemberDTO member = userMapper.findById(userId);
            if (member == null)
                return "회원 정보를 찾을 수 없습니다.";
            String userAddress = member.getAddress();

            // 1) 일단 영화관 목록은 Gateway에서 기본 제공 받음
            Map<String, Object> res = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(res.get("cinemas"));

            // 2) 사용자 입력에서 장소 키워드 추출
            // ex) "잠실역", "홍대", "건대입구"
            String keyword = kakaoLocalService.extractPlaceKeyword(userInput);

            Map<String, Object> coord;

            if (keyword != null) {
                // ⭐ 특정 장소 입력 있는 경우 → 그 장소 기준으로 정렬
                coord = kakaoLocalService.searchCoordinate(keyword);

                if (coord == null) {
                    // 특정 장소가 검색되지 않을 경우 → 사용자 주소 fallback
                    coord = kakaoLocalService.searchCoordinate(userAddress);
                }

            } else {
                // ⭐ 기본 시나리오 → 사용자 DB 주소 기준
                coord = kakaoLocalService.searchCoordinate(userAddress);
            }

            // 3) 거리 기준 정렬
            List<Map<String, Object>> sorted = kakaoLocalService.sortCinemasByDistance(
                    coord,
                    cinemas);

            // 세션 저장
            s.setLastCinemas(sorted);
            s.setStep(UserSession.Step.BRANCH_SELECT);

            return formatter.formatCinemas(sorted)
                    + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        // ------------------------------------------------
        // STEP 2: 지점 선택
        // ------------------------------------------------
        if (s.getStep() == UserSession.Step.BRANCH_SELECT) {

            String dateFilter = extractDateFilter(userInput);
            if (dateFilter != null)
                s.getBookingContext().put("dateFilter", dateFilter);

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
                            "dateFilter", s.getBookingContext().getOrDefault("dateFilter", "today")));

            List<Map<String, Object>> schedules = safeList(res.get("movies"));
            s.setLastMovies(schedules);
            s.setStep(UserSession.Step.MOVIE_SELECT);

            return "🎬 지점을 선택했습니다!\n"
                    + "지점: " + branchName + "\n\n"
                    + formatter.formatSchedules(schedules)
                    + "\n예매할 스케줄 번호를 입력해주세요. 예) 2번";
        }

        // ------------------------------------------------
        // STEP 3: 스케줄 선택
        // ------------------------------------------------
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
                    Map.of("scheduleNum", sel.get("scheduleNum")));

            List<Map<String, Object>> seats = safeList(res.get("seats"));
            s.setLastSeats(seats);
            s.setStep(UserSession.Step.SEAT_SELECT);

            String hiddenJson = String.format("{\"scheduleNum\":%s}", sel.get("scheduleNum"));

            return "🎥 **" + sel.get("movieTitle") + "** 영화를 선택하셨네요!\n\n"
                    + formatter.formatSeats(seats)
                    + "\n원하시는 좌석을 입력해주세요. (예: A3)\n\n"
                    + "💡 **Tip**: 좌석 배치도를 보고 싶으시면 **\"상세 좌석\"**이라고 말씀해주세요!"
                    + "\n" + hiddenJson;
        }

        // ------------------------------------------------
        // STEP 4: 좌석 선택 → 결제 확인
        // ------------------------------------------------
        if (s.getStep() == UserSession.Step.SEAT_SELECT) {

            // 쉼표(,) 또는 공백(\s)으로 구분하여 다중 입력 처리
            String[] inputs = userInput.split("[,\\s]+");
            List<Integer> seatCodes = new ArrayList<>();
            List<String> seatLabels = new ArrayList<>();
            int totalAmount = 0;
            String phoneNumber = null;

            MemberDTO member = userMapper.findById(userId);
            if (member == null)
                return "회원 정보를 찾을 수 없습니다.";
            phoneNumber = member.getPhoneNum();

            for (String rawInput : inputs) {
                String seatInput = rawInput.trim().toUpperCase();
                if (seatInput.isEmpty())
                    continue;

                Map<String, Object> seat = findSeatByLabel(s.getLastSeats(), seatInput);
                if (seat == null)
                    return "좌석 '" + seatInput + "'을(를) 찾을 수 없습니다. 다시 입력해주세요.";

                // 🚫 통로 좌석 체크 (DB 데이터 기반)
                int isAisle = 0;
                if (seat.get("is_aisle") != null) {
                    isAisle = Integer.parseInt(String.valueOf(seat.get("is_aisle")));
                }

                if (isAisle == 1) {
                    return "❌ 선택하신 '" + seatInput + "' 좌석은 통로입니다. 다른 좌석을 선택해주세요.";
                }

                if (Boolean.parseBoolean(String.valueOf(seat.get("reserved")))) {
                    return "❌ '" + seatInput + "' 좌석은 이미 예약되었습니다. 다른 좌석을 선택해주세요.";
                }

                int seatCode = Integer.parseInt(String.valueOf(seat.get("seat_code")));
                Object priceObj = seat.get("price");
                int pricePerSeat = (priceObj instanceof Number) ? ((Number) priceObj).intValue() : 12000;

                seatCodes.add(seatCode);
                seatLabels.add(seatInput);
                totalAmount += pricePerSeat;
            }

            if (seatCodes.isEmpty()) {
                return "좌석을 입력해주세요.";
            }

            // 좌석 정보 저장 (List 형태로 저장)
            s.getBookingContext().put("seatCodes", seatCodes);
            s.getBookingContext().put("phoneNumber", phoneNumber);
            s.getBookingContext().put("seatLabels", seatLabels);
            s.getBookingContext().put("amount", totalAmount);

            // JSON 형식으로 결제 정보 및 액션 타입 포함
            String jsonData = String.format(
                    "{\"actionType\":\"PAYMENT_CONFIRM\",\"amount\":%d,\"phone\":\"%s\",\"paymentType\":\"MOVIE\"}",
                    totalAmount, phoneNumber);

            // 다음 단계로 변경 (결제 대기)
            s.setStep(UserSession.Step.MOVIE_PAYMENT_CONFIRM);

            return " 좌석 선택이 완료되었습니다!\n\n"
                    + "선택한 좌석: " + String.join(", ", seatLabels) + "\n"
                    + "총 금액: " + totalAmount + "원\n"
                    + jsonData; // JSON 데이터를 텍스트에 포함
        }

        // ------------------------------------------------
        // STEP 5: 결제 확인 후 최종 예약 확정
        // ------------------------------------------------
        if (s.getStep() == UserSession.Step.MOVIE_PAYMENT_CONFIRM) {
            // 사용자 입력이 '결제'를 의미한다고 가정
            if (userInput.toLowerCase().contains("결제") || userInput.toLowerCase().contains("confirm")) {

                String scheduleNum = String.valueOf(s.getBookingContext().get("scheduleNum"));
                String phoneNumber = String.valueOf(s.getBookingContext().get("phoneNumber"));

                // ✅ 예약 그룹 ID 생성 (다중 좌석을 하나로 묶기 위함)
                String bookingGroupId = "BG" + System.currentTimeMillis();

                // 저장된 좌석 리스트 가져오기
                @SuppressWarnings("unchecked")
                List<Integer> seatCodes = (List<Integer>) s.getBookingContext().get("seatCodes");

                if (seatCodes == null || seatCodes.isEmpty()) {
                    // 하위 호환성: 단일 좌석 처리 (혹시 모를 경우 대비)
                    if (s.getBookingContext().containsKey("seatCode")) {
                        seatCodes = new ArrayList<>();
                        seatCodes.add((Integer) s.getBookingContext().get("seatCode"));
                    } else {
                        return "예약할 좌석 정보가 없습니다. 다시 시도해주세요.";
                    }
                }

                int successCount = 0;
                StringBuilder failMsg = new StringBuilder();

                // 각 좌석에 대해 예약 요청 (순차 처리) + ✅ 동일한 bookingGroupId 전달
                for (Integer code : seatCodes) {
                    Map<String, Object> res = intentService.processIntent(
                            "movie_booking_step4",
                            Map.of(
                                    "scheduleNum", scheduleNum,
                                    "seatCode", code,
                                    "phoneNumber", phoneNumber,
                                    "bookingGroupId", bookingGroupId)); // ✅ 그룹ID 전달

                    if (res.containsKey("message")) {
                        successCount++;
                    } else {
                        failMsg.append("좌석(ID:").append(code).append(") 실패: ").append(res.getOrDefault("error", "오류"))
                                .append("\n");
                    }
                }

                if (successCount > 0) {
                    // ⭐ 관리자 서버 전송 (일괄 처리)
                    int totalAmount = Integer.parseInt(String.valueOf(s.getBookingContext().get("amount")));
                    intentService.processIntent("movie_booking_finalize", Map.of(
                            "scheduleNum", scheduleNum,
                            "phoneNumber", phoneNumber,
                            "totalAmount", totalAmount));

                    // 📧 이메일 알림 발송 (알림 동의한 사용자만)
                    try {
                        MemberDTO member = userMapper.findById(userId);
                        if (member != null && "Y".equals(member.getNotificationStatus())) {
                            String movieTitle = String.valueOf(s.getBookingContext().get("movieTitle"));
                            String branchName = String.valueOf(s.getBookingContext().get("branchName"));
                            @SuppressWarnings("unchecked")
                            List<String> seatLabels = (List<String>) s.getBookingContext().get("seatLabels");
                            String seats = String.join(", ", seatLabels);

                            // 상영 시간 정보 (scheduleNum으로부터 조회하거나 기본값 사용)
                            String showtime = s.getBookingContext().getOrDefault("showtime", "예약 내역에서 확인").toString();

                            emailService.sendMovieReservationEmail(
                                    member.getEmail(),
                                    movieTitle,
                                    showtime + " (" + branchName + ")",
                                    seats,
                                    totalAmount);
                        }
                    } catch (Exception e) {
                        // 이메일 발송 실패해도 예약은 정상 완료
                        java.util.logging.Logger.getLogger(getClass().getName())
                                .warning("이메일 발송 실패: " + e.getMessage());
                    }

                    s.reset();
                    String msg = "🎉 총 " + successCount + "개의 좌석 예매가 완료되었습니다!";
                    if (failMsg.length() > 0) {
                        msg += "\n\n⚠️ 일부 좌석 예약 실패:\n" + failMsg.toString();
                    }

                    return msg + "\n\n"
                            + "상세 내역은 사이드바의 [예약 내역] 페이지에서 확인하실 수 있습니다.\n"
                            + "또 도와드릴까요? 😊";
                }

                s.reset();
                return "❌ 예매 실패:\n" + failMsg.toString();
            } else {
                return "결제를 진행해 주시거나, 결제를 취소하시려면 '취소'를 입력해 주세요.";
            }
        }

        return "처리할 수 없는 상태입니다. 다시 시도해주세요.";
    }

}
