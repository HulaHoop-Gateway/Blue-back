package com.hulahoop.blueback.ai.model.service.bike;

import com.hulahoop.blueback.ai.model.service.MembershipVerificationService;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.stereotype.Component;

@Component
public class BikeFlowRouter {

    private final BikeFlowHandler bikeFlowHandler;
    private final MembershipVerificationService membershipVerificationService;

    public BikeFlowRouter(BikeFlowHandler bikeFlowHandler,
            MembershipVerificationService membershipVerificationService) {
        this.bikeFlowHandler = bikeFlowHandler;
        this.membershipVerificationService = membershipVerificationService;
    }

    public String handle(String userInput, UserSession session, String userId) {

        if (userInput == null || userInput.isBlank()) {
            return "다시 입력해주세요.";
        }

        String lower = userInput.toLowerCase().trim();

        // 취소 의도 처리 (예매 흐름과 동일)
        if (isCancelCommand(lower)) {
            session.reset();
            return "자전거 예약을 종료했습니다. 필요하시면 다시 말씀해주세요.";
        }

        // 이미 자전거 예약 흐름 진행 중이면 계속 처리
        if (session.getStep() != UserSession.Step.IDLE) {
            return bikeFlowHandler.handle(userInput, session, userId);
        }

        // 최초 진입 조건
        if (containsBikeKeyword(lower)) {
            // 🔹 자전거 회원 검증
            String phoneNumber = membershipVerificationService.getUserPhoneNumber(userId);
            if (phoneNumber == null) {
                return "회원 정보를 찾을 수 없습니다.";
            }

            if (!membershipVerificationService.isBikeMember(phoneNumber)) {
                return "❌ 죄송합니다. 자전거 대여 서비스에 가입되지 않은 회원입니다.\n" +
                        "먼저 자전거 대여 앱에서 회원가입을 진행해주세요.";
            }

            return bikeFlowHandler.handle(userInput, session, userId);
        }

        // 자전거 관련이 아닌 경우
        return "처리할 수 없는 요청입니다. 자전거 예약을 원하시면 말씀해주세요.";
    }

    private boolean containsBikeKeyword(String text) {
        return text.contains("자전거")
                || text.contains("대여")
                || text.contains("예약")
                || text.contains("따릉이");
    }

    private boolean isCancelCommand(String text) {
        return text.equals("취소") ||
                text.equals("종료") ||
                text.equals("그만") ||
                text.equals("안할래") ||
                text.equals("끝") ||
                text.equals("나가기");
    }
}
