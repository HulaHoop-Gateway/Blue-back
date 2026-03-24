package com.hulahoop.blueback.ai.model.service.bike;

import com.hulahoop.blueback.ai.model.service.IntentService;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import com.hulahoop.blueback.email.model.service.EmailService;
import com.hulahoop.blueback.kakao.model.service.KakaoLocalService;
import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BikeFlowHandler {

    private final IntentService intentService;
    private final KakaoLocalService kakaoLocalService;
    private final UserMapper userMapper;
    private final EmailService emailService;

    public BikeFlowHandler(IntentService intentService,
            KakaoLocalService kakaoLocalService,
            UserMapper userMapper,
            EmailService emailService) {
        this.intentService = intentService;
        this.kakaoLocalService = kakaoLocalService;
        this.userMapper = userMapper;
        this.emailService = emailService;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : new ArrayList<>();
    }

    public String handle(String userInput, UserSession session, String userId) {

        // -- 단계 1: 자전거 목록 조회 및 사용자 위치 기반 거리 정렬 --
        if (session.getStep() == UserSession.Step.IDLE) {

            MemberDTO member = userMapper.findById(userId);
            if (member == null) {
                return "회원 정보를 찾을 수 없습니다.";
            }
            String userAddress = member.getAddress();

            // 백엔드를 통해 현재 대여 가능한 자전거 목록을 모두 가져옴
            Map<String, Object> res = intentService.processIntent("bike_list", Map.of());
            List<Map<String, Object>> bikes = safeList(res.get("bicycles"));

            if (bikes.isEmpty()) {
                return "현재 대여 가능한 자전거가 없습니다.";
            }

            // 사용자가 입력한 문장에서 장소 키워드(예: 홍대)를 추출 시도
            String keyword = kakaoLocalService.extractPlaceKeyword(userInput);
            Map<String, Object> coord;

            if (keyword != null) {
                coord = kakaoLocalService.searchCoordinate(keyword);
                if (coord == null) {
                    coord = kakaoLocalService.searchCoordinate(userAddress);
                }
            } else {
                // 특정 장소 언급 없으면 회원 정보에 등록된 주소 기준으로 탐색
                coord = kakaoLocalService.searchCoordinate(userAddress);
            }

            // 카카오 로컬 서비스 이용해서 거리순 정렬 후 가까운 순서대로 표시
            List<Map<String, Object>> sorted = kakaoLocalService.sortBikesByDistance(coord, bikes);

            session.setLastBikes(sorted);
            session.setStep(UserSession.Step.BIKE_SELECT);

            StringBuilder sb = new StringBuilder("가까운 자전거 목록\n\n");
            int i = 1;
            for (Map<String, Object> b : sorted) {
                double dist = b.get("distance") != null
                        ? Math.round(((double) b.get("distance")) * 10) / 10.0
                        : -1;

                sb.append(i++)
                        .append(") ")
                        .append(b.get("bicycleCode"))
                        .append(" (")
                        .append(b.get("bicycleType"))
                        .append(") - ")
                        .append(dist)
                        .append(" km\n");
            }

            return sb.append("\n예약하실 자전거 번호를 입력해주세요. 예) 1번").toString();
        }

        // -- 단계 2: 자전거 선택 (시간당 요금 조회 후 유효성 검사) --
        if (session.getStep() == UserSession.Step.BIKE_SELECT) {

            Integer idx = extractNumber(userInput, session.getLastBikes().size());
            if (idx == null) {
                return "자전거 번호를 다시 입력해주세요. 예) 1번";
            }

            Map<String, Object> selectedBike = session.getLastBikes().get(idx - 1);
            String bicycleType = String.valueOf(selectedBike.get("bicycleType"));

            // 선택한 자전거 타입에 맞는 시간당 대여 요금을 조회
            Map<String, Object> rateRes = intentService.processIntent("bike_rate", Map.of("bicycleType", bicycleType));

            Object rateObj = rateRes.get("ratePerHour");
            int ratePerHour = (rateObj instanceof Number) ? ((Number) rateObj).intValue() : 0;

            // 요금 정보가 없거나 0원인 경우 치명적인 오류로 간주하고 백업
            if (ratePerHour <= 0) {
                session.reset();
                return "선택하신 자전거의 시간당 요금이 0원 이하입니다. 죄송하지만 예약이 불가능합니다. 처음부터 다시 시도해주세요.";
            }

            session.getBookingContext().put("bicycleCode", selectedBike.get("bicycleCode"));
            session.getBookingContext().put("bicycleType", bicycleType);
            session.getBookingContext().put("ratePerHour", ratePerHour);

            int ratePerMinuteDisplay = ratePerHour / 60;

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime limit = now.plusHours(2);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");

            session.setStep(UserSession.Step.BIKE_TIME_INPUT);

            return "선택하신 자전거는 **" + bicycleType + "** 입니다.\n"
                    + "현재시간 기준 예약 가능 시간은 아래와 같습니다.\n\n"
                    + "가능 시간: " + now.format(fmt) + " ~ " + limit.format(fmt) + "\n"
                    + "분당 요금: " + ratePerMinuteDisplay + "원\n\n"
                    + "이용하실 시간을 입력해주세요.\n"
                    + "예) 18:30 ~ 19:00";
        }

        // -- 단계 3: 이용 시간 입력 후 예상 금액 안내 (결제 대기 상태로 넘어감) --
        if (session.getStep() == UserSession.Step.BIKE_TIME_INPUT) {

            String[] parts = userInput.split("~");
            if (parts.length != 2) {
                return "시간 형식이 올바르지 않습니다. 예) 18:30 ~ 19:00";
            }

            String start = parts[0].trim().replaceAll("[^0-9:]", "");
            String end = parts[1].trim().replaceAll("[^0-9:]", "");

            session.getBookingContext().put("startTime", start);
            session.getBookingContext().put("endTime", end);

            int ratePerHour = (session.getBookingContext().get("ratePerHour") instanceof Number)
                    ? (int) session.getBookingContext().get("ratePerHour")
                    : 0;

            long minutes = calculateMinutes(start, end);

            // 입력받은 시간을 바탕으로 총 결제 금액 계산
            double totalAmountDouble = ((double) ratePerHour * minutes) / 60.0;
            int amount = (int) Math.round(totalAmountDouble);

            String phone = getUserPhone(userId);
            session.getBookingContext().put("phoneNumber", phone);
            session.getBookingContext().put("amount", amount);

            // 프론트엔드가 결제 버튼을 띄울 수 있도록 JSON 문자열 삽입
            String jsonData = String.format(
                    "{\"actionType\":\"PAYMENT_CONFIRM\",\"amount\":%d,\"phone\":\"%s\",\"paymentType\":\"BICYCLE\"}",
                    amount, phone);

            session.setStep(UserSession.Step.BIKE_PAYMENT_CONFIRM);

            return "자전거 선택이 완료되었습니다!\n\n"
                    + "이용 시간: " + start + " ~ " + end + "\n"
                    + "**총 결제 금액: " + String.format("%,d", amount) + "원**\n\n"
                    + "아래 [결제하기] 버튼을 눌러 결제를 진행해주세요.\n\n"
                    + jsonData;
        }

        // -- 단계 4: 결제 확인 후 실제 예약 확정 API 호출 및 메일 전송 --
        if (session.getStep() == UserSession.Step.BIKE_PAYMENT_CONFIRM) {

            if (userInput.toLowerCase().contains("결제") || userInput.toLowerCase().contains("confirm")) {

                Map<String, Object> bookingReq = new HashMap<>();
                bookingReq.putAll(session.getBookingContext());
                bookingReq.put("userId", userId);

                Map<String, Object> bookingRes = intentService.processIntent("bike_booking_step3", bookingReq);

                String message = (String) bookingRes.get("message");
                Object bookingIdObj = bookingRes.get("bookingId");
                String bookingId = bookingIdObj != null ? String.valueOf(bookingIdObj) : "unknown";

                if ("success".equals(message)) {

                    try {
                        MemberDTO member = userMapper.findById(userId);
                        if (member != null && "Y".equals(member.getNotificationStatus())) {

                            String bicycleCode = String.valueOf(session.getBookingContext().get("bicycleCode"));
                            String bicycleType = String.valueOf(session.getBookingContext().get("bicycleType"));

                            String bikeName = bicycleType + " (" + bicycleCode + ")";
                            String location = "대여 지점 정보는 예약 내역에서 확인";

                            String startTime = String.valueOf(session.getBookingContext().get("startTime"));
                            String endTime = String.valueOf(session.getBookingContext().get("endTime"));

                            String rentalTime;
                            if (startTime != null && !startTime.equals("null") && endTime != null
                                    && !endTime.equals("null")) {
                                rentalTime = startTime + " ~ " + endTime;
                            } else {
                                rentalTime = "예약 내역에서 확인";
                            }

                            int amount = Integer.parseInt(String.valueOf(session.getBookingContext().get("amount")));

                            emailService.sendBikeReservationEmail(
                                    member.getEmail(),
                                    bikeName,
                                    rentalTime,
                                    location,
                                    amount);
                        }
                    } catch (Exception e) {
                        java.util.logging.Logger.getLogger(getClass().getName())
                                .warning("이메일 발송 실패: " + e.getMessage());
                    }

                    session.reset();
                    return "**자전거 예약이 완료되었습니다!**\n\n"
                            + "상세 내역은 사이드바의 [예약 내역] 페이지에서 확인하실 수 있습니다.\n"
                            + "또 도와드릴까요?";
                } else {
                    session.reset();
                    return "죄송합니다. 예약 과정에서 오류가 발생했습니다. 다시 시도해 주세요.";
                }
            } else {
                return "결제를 진행해 주시거나, 결제를 취소하시려면 '취소'를 입력해 주세요.";
            }
        }

        return "처리할 수 없는 단계입니다. 다시 시도해주세요.";
    }

    private Integer extractNumber(String input, int maxSize) {
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.isEmpty())
            return null;
        int v = Integer.parseInt(digits);
        return (v >= 1 && v <= maxSize) ? v : null;
    }

    // 예약 시작/종료 문자열을 파싱해서 몇 분 동안 예약했는지 계산
    private long calculateMinutes(String startTime, String endTime) {
        try {
            if (startTime.length() != 5 || endTime.length() != 5) {
                throw new IllegalArgumentException("Invalid time format");
            }

            LocalTime start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"));

            Duration duration = Duration.between(start, end);
            long minutes = duration.toMinutes();

            // 밤을 넘겨 다음 날로 이어지는 예약 (예: 23:00 ~ 01:00) 처리
            if (minutes < 0) {
                minutes += 24 * 60;
            }

            return minutes;
        } catch (Exception e) {
            return 30; // 파싱 실패 시 기본 30분
        }
    }

    private String getUserPhone(String userId) {
        MemberDTO member = userMapper.findById(userId);
        if (member != null) {
            return member.getPhoneNum();
        }
        return "01000000000";
    }
}