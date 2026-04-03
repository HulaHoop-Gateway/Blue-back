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

        // 예약 중간이라도 취소 요청이 들어오면 세션을 바로 초기화함
        if (isCancelCommand(lower)) {
            session.reset();
            return "자전거 예약을 종료했습니다. 필요하시면 다시 말씀해주세요.";
        }

        // 예약 진행 중인 세션이면 입력값을 바로 핸들러로 넘김
        if (session.getStep() != UserSession.Step.IDLE) {
            return bikeFlowHandler.handle(userInput, session, userId);
        }

        // 초기 상태에서 자전거 관련 키워드를 말했을 때 진입점
        if (containsBikeKeyword(lower)) {

            // 게이트웨이 통해 자전거 서비스 쪽에 가입된 회원인지 확인먼저 함
            String phoneNumber = membershipVerificationService.getUserPhoneNumber(userId);
            if (phoneNumber == null) {
                return "회원 정보를 찾을 수 없습니다.";
            }

            if (!membershipVerificationService.isBikeMember(phoneNumber)) {
                return "죄송합니다. 자전거 대여 서비스에 가입되지 않은 회원입니다.\n" +
                        "먼저 자전거 대여 앱에서 회원가입을 진행해주세요.";
            }

            return bikeFlowHandler.handle(userInput, session, userId);
        }

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
