package com.hulahoop.blueback.ai.model.service.movie;

import com.hulahoop.blueback.ai.model.service.IntentService;
import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MovieCancelHandler {

    private final IntentService intentService;
    private final UserMapper userMapper;

    // 유저 상태 저장
    private final Map<String, String> userState = new HashMap<>();
    // 선택된 예매번호 저장
    private final Map<String, String> selectedReservation = new HashMap<>();

    public MovieCancelHandler(IntentService intentService, UserMapper userMapper) {
        this.intentService = intentService;
        this.userMapper = userMapper;
    }

    /**
     * 🔥 MovieFlowRouter에서 사용되는 핵심 함수
     * → 유저가 현재 취소 진행중인지 판단한다.
     */
    public boolean isInCancelFlow(String userId) {
        String state = userState.get(userId);
        return state != null && !state.equals("idle");
    }

    public String handle(String userInput, String userId) {

        // 회원 정보 조회
        MemberDTO member = userMapper.findById(userId);
        if (member == null)
            return "❌ 회원 정보를 찾을 수 없습니다. 로그인 상태를 확인해주세요.";

        String phoneNumber = member.getPhoneNum();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "⚠️ 회원 정보에 전화번호가 등록되어 있지 않습니다. 고객센터에 문의해주세요.";
        }

        // 현재 상태 불러오기
        String currentState = userState.getOrDefault(userId, "idle");

        Map<String, Object> data = new HashMap<>();
        data.put("phoneNumber", phoneNumber);

        // 1️⃣ 취소 흐름 시작
        if (userInput.matches("(?i)^예매 취소.*|^2번$")) {
            userState.put(userId, "awaiting_reservation_num");

            Map<String, Object> res = intentService.processIntent("movie_cancel_step1", data);
            return buildResponse(res, "📋 취소 가능한 예매 내역입니다:\n\n", true);
        }

        // 2️⃣ 예매 번호 입력 단계
        if (currentState.equals("awaiting_reservation_num") && userInput.matches("^\\d{10}$")) {

            userState.put(userId, "awaiting_confirmation");
            selectedReservation.put(userId, userInput);

            data.put("reservationNum", userInput);
            Map<String, Object> res = intentService.processIntent("movie_cancel_step2", data);

            return res.getOrDefault("message", "❌ 예매 정보를 찾을 수 없습니다.").toString();
        }

        // 3️⃣ 취소 거절
        if (currentState.equals("awaiting_confirmation") &&
                List.of("아니오", "취소", "안할래", "그만", "아니", "안돼").stream()
                        .anyMatch(p -> p.equalsIgnoreCase(userInput))) {

            userState.remove(userId);
            selectedReservation.remove(userId);
            return "🚫 예매 취소가 취소되었습니다. 다른 작업을 원하시면 메뉴를 선택해주세요.";
        }

        // 4️⃣ 취소 확정
        if (currentState.equals("awaiting_confirmation") &&
                List.of("네", "예", "응", "그래", "좋아", "ㅇㅇ", "오케이").stream()
                        .anyMatch(p -> p.equalsIgnoreCase(userInput))) {

            String reservationNum = selectedReservation.get(userId);
            data.put("reservationNum", reservationNum);

            // 상태 초기화
            userState.remove(userId);
            selectedReservation.remove(userId);

            Map<String, Object> res = intentService.processIntent("movie_cancel_step3", data);
            return res.getOrDefault("message", "⚠️ 예매 취소 처리 중 오류가 발생했습니다.").toString();
        }

        return "❓ 잘못된 입력입니다. '예매 취소'라고 입력하시면 취소 가능한 내역을 보여드릴게요.";
    }

    /**
     * 취소 가능한 예매 목록을 이쁘게 출력하는 Formatter (그룹화 지원)
     */
    private String buildResponse(Map<String, Object> res, String header, boolean showPrompt) {

        if (res.containsKey("message"))
            return res.get("message").toString();

        List<Map<String, Object>> reservations = (List<Map<String, Object>>) res.get("reservations");

        if (reservations == null || reservations.isEmpty()) {
            return "📭 취소 가능한 예매 내역이 없습니다.";
        }

        StringBuilder sb = new StringBuilder(header);

        for (Map<String, Object> r : reservations) {
            String bookingGroupId = (String) r.get("bookingGroupId");
            Object seatLabelsObj = r.get("seatLabels");

            // 좌석 표시 (그룹화 지원)
            String seatDisplay;
            if (seatLabelsObj instanceof List) {
                List<String> seatLabels = (List<String>) seatLabelsObj;
                seatDisplay = String.join(", ", seatLabels);
            } else {
                seatDisplay = String.valueOf(r.get("seat"));
            }

            // 그룹 표시 (여러 좌석인 경우 개수 표시)
            String groupIndicator = "";
            if (seatLabelsObj instanceof List && ((List<?>) seatLabelsObj).size() > 1) {
                groupIndicator = " (총 " + ((List<?>) seatLabelsObj).size() + "석)";
            }

            sb.append("🎟️ ")
                    .append(r.get("movieTitle")).append(" / ")
                    .append(r.get("screeningDate")).append(" / ")
                    .append(r.get("branchName")).append(" / ")
                    .append("좌석 ").append(seatDisplay).append(groupIndicator).append(" / ")
                    .append("번호: ").append(r.get("reservationNum"))
                    .append("\n");
        }

        if (showPrompt)
            sb.append("\n💡 취소하실 예매 번호를 입력해주세요 (예: 2511130003)");

        return sb.toString();
    }
}
