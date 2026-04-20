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

    // 현재 사용자가 취소 플로우의 어느 단계에 있는지 기록해두는 맵
    private final Map<String, String> userState = new HashMap<>();
    // 사용자가 취소하겠다고 선택한 예매 번호를 임시로 저장해두는 맵
    private final Map<String, String> selectedReservation = new HashMap<>();

    public MovieCancelHandler(IntentService intentService, UserMapper userMapper) {
        this.intentService = intentService;
        this.userMapper = userMapper;
    }

    // MovieFlowRouter에서 모든 요청 전에 취소가 먼저 진행 중인지 확인할 때 호출됨
    public boolean isInCancelFlow(String userId) {
        String state = userState.get(userId);
        return state != null && !state.equals("idle");
    }

    public String handle(String userInput, String userId) {

        // 먼저 회원이 맞는지, 그리고 DB에 번호가 잘 들어있는지 확인
        MemberDTO member = userMapper.findById(userId);
        if (member == null)
            return "회원 정보를 찾을 수 없습니다. 로그인 상태를 확인해주세요.";

        String phoneNumber = member.getPhoneNum();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "회원 정보에 전화번호가 등록되어 있지 않습니다. 고객센터에 문의해주세요.";
        }

        // 현재 유저의 취소 플로우 진행 단계를 꺼냄 (없으면 idle)
        String currentState = userState.getOrDefault(userId, "idle");

        Map<String, Object> data = new HashMap<>();
        data.put("phoneNumber", phoneNumber);

        // -- 단계 1: 취소 흐름 시작 --
        // 취소하겠다는 명확한 단어가 있거나 메뉴에서 2번을 눌렀을 때
        if (userInput.matches("(?i)^예매 취소.*|^2번$")) {
            userState.put(userId, "awaiting_reservation_num");

            Map<String, Object> res = intentService.processIntent("movie_cancel_step1", data);
            return buildResponse(res, "취소 가능한 예매 내역입니다:\n\n", true);
        }

        // -- 단계 2: 예매 번호(숫자 10자리) 입력 --
        // 방금 예매 내역을 보여줬고, 유저가 10자리 숫자를 입력한 상태
        if (currentState.equals("awaiting_reservation_num") && userInput.matches("^\\d{10}$")) {

            userState.put(userId, "awaiting_confirmation");
            selectedReservation.put(userId, userInput);

            data.put("reservationNum", userInput);
            Map<String, Object> res = intentService.processIntent("movie_cancel_step2", data);

            return res.getOrDefault("message", "예매 정보를 찾을 수 없습니다.").toString();
        }

        // -- 단계 3: 취소 과정 중단 --
        // 유저가 진짜 취소할 거냐고 물어봤을 때 거절한 경우
        if (currentState.equals("awaiting_confirmation") &&
                List.of("아니오", "취소", "안할래", "그만", "아니", "안돼").stream()
                        .anyMatch(p -> p.equalsIgnoreCase(userInput))) {

            userState.remove(userId);
            selectedReservation.remove(userId);
            return "예매 취소가 중단되었습니다. 다른 작업을 원하시면 다시 말씀해주세요.";
        }

        // -- 단계 4: 취소 최종 확정 --
        // 유저가 동의했을 때 실제 취소 API 찌름
        if (currentState.equals("awaiting_confirmation") &&
                List.of("네", "예", "응", "그래", "좋아", "ㅇㅇ", "오케이").stream()
                        .anyMatch(p -> p.equalsIgnoreCase(userInput))) {

            String reservationNum = selectedReservation.get(userId);
            data.put("reservationNum", reservationNum);

            // 처리가 끝나면 상태 초기화
            userState.remove(userId);
            selectedReservation.remove(userId);

            Map<String, Object> res = intentService.processIntent("movie_cancel_step3", data);
            return res.getOrDefault("message", "예매 취소 처리 중 오류가 발생했습니다.").toString();
        }

        return "잘못된 입력입니다. '예매 취소'라고 입력하시면 취소 가능한 내역을 보여드릴게요.";
    }

    // 예매 목록을 파싱해서 채팅창에 보기 편하게 텍스트로 만들어주는 헬퍼 메서드
    private String buildResponse(Map<String, Object> res, String header, boolean showPrompt) {

        if (res.containsKey("message"))
            return res.get("message").toString();

        List<Map<String, Object>> reservations = (List<Map<String, Object>>) res.get("reservations");

        if (reservations == null || reservations.isEmpty()) {
            return "취소 가능한 예매 내역이 없습니다.";
        }

        StringBuilder sb = new StringBuilder(header);

        for (Map<String, Object> r : reservations) {
            String bookingGroupId = (String) r.get("bookingGroupId");
            Object seatLabelsObj = r.get("seatLabels");

            // 같이 예매한 여러 좌석을 한 번에 묶어서 표시
            String seatDisplay;
            if (seatLabelsObj instanceof List) {
                List<String> seatLabels = (List<String>) seatLabelsObj;
                seatDisplay = String.join(", ", seatLabels);
            } else {
                seatDisplay = String.valueOf(r.get("seat"));
            }

            String groupIndicator = "";
            if (seatLabelsObj instanceof List && ((List<?>) seatLabelsObj).size() > 1) {
                groupIndicator = " (총 " + ((List<?>) seatLabelsObj).size() + "석)";
            }

            sb.append("- ")
                    .append(r.get("movieTitle")).append(" / ")
                    .append(r.get("screeningDate")).append(" / ")
                    .append(r.get("branchName")).append(" / ")
                    .append("좌석 ").append(seatDisplay).append(groupIndicator).append(" / ")
                    .append("번호: ").append(r.get("reservationNum"))
                    .append("\n");
        }

        if (showPrompt)
            sb.append("\n취소하실 예매 번호를 입력해주세요 (예: 2511130003)");

        return sb.toString();
    }
}
