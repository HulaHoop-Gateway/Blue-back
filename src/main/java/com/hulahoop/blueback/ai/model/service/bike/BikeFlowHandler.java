package com.hulahoop.blueback.ai.model.service.bike;

import com.hulahoop.blueback.ai.model.service.IntentService;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BikeFlowHandler {

    private final IntentService intentService;

    public BikeFlowHandler(IntentService intentService) {
        this.intentService = intentService;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : new ArrayList<>();
    }

    public String handle(String userInput, UserSession session, String userId) {

        // STEP 1: 자전거 목록 (수정 없음)
        if (session.getStep() == UserSession.Step.IDLE) {
            // ... (기존 로직 유지)

            Map<String, Object> res = intentService.processIntent("bike_list", Map.of());
            List<Map<String, Object>> bikes = safeList(res.get("bicycles"));

            if (bikes.isEmpty()) {
                return "현재 대여 가능한 자전거가 없습니다.";
            }

            session.setLastBikes(bikes);
            session.setStep(UserSession.Step.BIKE_SELECT);

            StringBuilder sb = new StringBuilder("현재 이용 가능한 자전거 목록입니다:\n\n");
            int i = 1;
            for (Map<String, Object> b : bikes) {
                sb.append(i++)
                        .append(") ")
                        .append(b.get("bicycleCode"))
                        .append(" - ")
                        .append(b.get("bicycleType"))
                        .append("\n");
            }

            return sb.append("\n예약하실 자전거 번호를 입력해주세요. 예) 1번").toString();
        }

        // STEP 2: 자전거 선택 (요금 조회 및 유효성 검사 로직 추가)
        if (session.getStep() == UserSession.Step.BIKE_SELECT) {

            Integer idx = extractNumber(userInput, session.getLastBikes().size());
            if (idx == null) {
                return "자전거 번호를 다시 입력해주세요. 예) 1번";
            }

            Map<String, Object> selectedBike = session.getLastBikes().get(idx - 1);
            String bicycleType = String.valueOf(selectedBike.get("bicycleType"));

            // 1. bike_rate 인텐트 호출
            Map<String, Object> rateRes =
                    intentService.processIntent("bike_rate", Map.of("bicycleType", bicycleType));

            Object rateObj = rateRes.get("ratePerHour");
            int ratePerHour = (rateObj instanceof Number) ? ((Number) rateObj).intValue() : 0;

            // 🚨 핵심 로직: 요금 유효성 검사 (0원 문제 해결)
            if (ratePerHour <= 0) {
                // 치명적인 에러 조건 발생 시 session.reset() 및 친절한 오류 메시지 전달
                session.reset();
                return "선택하신 자전거의 시간당 요금이 0원 이하입니다. 죄송하지만 예약이 불가능합니다. 처음부터 다시 시도해주세요.";
            }

            session.getBookingContext().put("bicycleCode", selectedBike.get("bicycleCode"));
            session.getBookingContext().put("bicycleType", bicycleType);
            session.getBookingContext().put("ratePerHour", ratePerHour); // 정확한 금액 계산을 위해 저장

            // 표시용 분당 요금 (정수 나누기 결과)
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

        // STEP 3: 시간 입력 처리 -> 결제 확인 JSON 출력 (수정 없음)
        if (session.getStep() == UserSession.Step.BIKE_TIME_INPUT) {
            // ... (기존 로직 유지)
            String[] parts = userInput.split("~");
            if (parts.length != 2) {
                return "시간 형식이 올바르지 않습니다. 예) 18:30 ~ 19:00";
            }

            String start = parts[0].trim().replaceAll("[^0-9:]", "");
            String end = parts[1].trim().replaceAll("[^0-9:]", "");

            session.getBookingContext().put("startTime", start);
            session.getBookingContext().put("endTime", end);

            int ratePerHour = (session.getBookingContext().get("ratePerHour") instanceof Number)
                    ? (int) session.getBookingContext().get("ratePerHour") : 0;

            long minutes = calculateMinutes(start, end);

            // 💡 금액 계산: (시간당 요금 * 분) / 60.0 (실수 나누기 후 반올림하여 0원 오류 방지)
            double totalAmountDouble = ((double) ratePerHour * minutes) / 60.0;
            int amount = (int) Math.round(totalAmountDouble);

            // 사용자 전화번호 가져오기
            String phone = getUserPhone(userId);

            // JSON 형식으로 결제 정보 및 액션 타입 포함
            String jsonData = String.format(
                    "{\"actionType\":\"PAYMENT_CONFIRM\",\"amount\":%d,\"phone\":\"%s\"}",
                    amount, phone
            );

            // 다음 단계로 변경 (결제 대기)
            session.setStep(UserSession.Step.BIKE_PAYMENT_CONFIRM);

            return "예약 정보가 확인되었습니다.\n\n"
                    + "이용 시간: " + start + " ~ " + end + "\n"
                    + jsonData; // JSON 데이터를 텍스트에 포함
        }

        // 🆕 STEP 4: 결제 확인 후 최종 예약 확정 로직 (bike_booking_step3 호출)
        // 결제 모듈에서 받은 응답(예: "결제 완료")을 userInput으로 받아서 처리한다고 가정합니다.
        if (session.getStep() == UserSession.Step.BIKE_PAYMENT_CONFIRM) {
            // 사용자 입력이 '결제 완료'를 의미한다고 가정 (실제로는 AI가 상태를 파악)
            if (userInput.toLowerCase().contains("결제") || userInput.toLowerCase().contains("confirm")) {

                // 2. bike_booking_step3 인텐트 호출 (최종 예약)
                Map<String, Object> bookingReq = new HashMap<>();
                bookingReq.putAll(session.getBookingContext()); // 컨텍스트의 모든 데이터를 백엔드로 전달
                bookingReq.put("userId", userId);

                Map<String, Object> bookingRes = intentService.processIntent("bike_booking_step3", bookingReq);

                String message = (String) bookingRes.get("message");
                Integer bookingId = (Integer) bookingRes.get("bookingId");

                // ✅ 핵심 로직: message: "success" 응답 확인
                if ("success".equals(message)) {
                    session.reset(); // 예약 성공 시 세션 초기화
                    return "🎉 자전거 예약이 성공적으로 완료되었습니다.\n"
                            + "예약 번호: **" + bookingId + "**\n"
                            + "즐거운 시간 되세요!";
                } else {
                    session.reset(); // 예약 실패 시 세션 초기화 및 오류 처리
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
        if (digits.isEmpty()) return null;
        int v = Integer.parseInt(digits);
        return (v >= 1 && v <= maxSize) ? v : null;
    }

    /**
     * 시간 차이를 분 단위로 계산
     */
    private long calculateMinutes(String startTime, String endTime) {
        try {
            // HH:mm 포맷 확인
            if (startTime.length() != 5 || endTime.length() != 5) {
                throw new IllegalArgumentException("Invalid time format");
            }

            // LocalTime 파싱 (예: "18:30")
            LocalTime start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"));

            Duration duration = Duration.between(start, end);
            long minutes = duration.toMinutes();

            // 종료 시간이 시작 시간보다 빠른 경우 (자정을 넘은 경우) 24시간을 더함
            if (minutes < 0) {
                minutes += 24 * 60;
            }

            return minutes;
        } catch (Exception e) {
            // 오류 발생 시 기본값 (예: 30분) 반환
            return 30;
        }
    }

    /**
     * 사용자 전화번호 가져오기
     * TODO: 실제 사용자 정보 조회 로직으로 변경 필요
     */
    private String getUserPhone(String userId) {
        // 실제 구현에서는 userId를 사용하여 DB 또는 세션에서 조회해야 합니다.
        // 현재는 임시 전화번호를 반환합니다.
        return "01012345678"; // 하이픈 없이 11자리
    }
}