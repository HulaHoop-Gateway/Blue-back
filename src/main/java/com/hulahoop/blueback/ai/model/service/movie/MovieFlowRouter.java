package com.hulahoop.blueback.ai.model.service.movie;

import com.hulahoop.blueback.ai.model.service.session.UserSession;
import com.hulahoop.blueback.ai.model.service.MembershipVerificationService;
import org.springframework.stereotype.Component;

@Component
public class MovieFlowRouter {

    private final MovieIntentResolver intentResolver;
    private final MovieBookingFlowHandler bookingHandler;
    private final MovieLookUpHandler lookupHandler;
    private final MovieCancelHandler cancelHandler;
    private final MembershipVerificationService membershipVerificationService;

    public MovieFlowRouter(MovieIntentResolver intentResolver,
            MovieBookingFlowHandler bookingHandler,
            MovieLookUpHandler lookupHandler,
            MovieCancelHandler cancelHandler,
            MembershipVerificationService membershipVerificationService) {
        this.intentResolver = intentResolver;
        this.bookingHandler = bookingHandler;
        this.lookupHandler = lookupHandler;
        this.cancelHandler = cancelHandler;
        this.membershipVerificationService = membershipVerificationService;
    }

    public boolean isInCancelFlow(String userId) {
        return cancelHandler.isInCancelFlow(userId);
    }

    public String handle(String userInput, UserSession session, String userId) {

        // 취소 로직이 진행 중이면 무조건 취소 핸들러로 먼저 다 넘김
        if (cancelHandler.isInCancelFlow(userId)) {
            return cancelHandler.handle(userInput, userId);
        }

        MovieIntentResolver.MovieIntent intent = intentResolver.resolve(userInput);

        System.out.println("현재 상태: " + session.getStep());
        System.out.println("해석된 intent: " + intent);

        // 예매가 진행 중인 상태 처리 (IDLE이 아닌 경우)
        if (session.getStep() != UserSession.Step.IDLE) {

            // 진행 중에 사용자가 명시적으로 새로 시작하겠다고 한 경우
            if (intent == MovieIntentResolver.MovieIntent.START_BOOKING) {
                System.out.println("예매 재시작 요청 감지 -> 세션 초기화");

                // 영화관 회원인지 게이트웨이 통해서 다시 조회
                String phoneNumber = membershipVerificationService.getUserPhoneNumber(userId);
                if (phoneNumber == null) {
                    return "회원 정보를 찾을 수 없습니다.";
                }

                if (!membershipVerificationService.isCinemaMember(phoneNumber)) {
                    return "죄송합니다. 노바시네마에 가입되지 않은 회원입니다.\n" +
                            "먼저 노바시네마 앱에서 회원가입을 진행해주세요.";
                }

                session.reset();
                session.setFlowType(UserSession.FlowType.MOVIE);
                return bookingHandler.handle(userInput, session, userId);
            }

            // 그냥 다음 단계 입력이면 계속 예약 진행
            System.out.println("예매 흐름 유지: " + session.getStep());
            return bookingHandler.handle(userInput, session, userId);
        }

        // 아무 작업도 안 하던 초기(IDLE) 상태 처리
        return switch (intent) {
            case START_BOOKING -> {
                // 예약 시작 시에도 당연히 회원 여부 검사
                String phoneNumber = membershipVerificationService.getUserPhoneNumber(userId);
                if (phoneNumber == null) {
                    yield "회원 정보를 찾을 수 없습니다.";
                }

                if (!membershipVerificationService.isCinemaMember(phoneNumber)) {
                    yield "죄송합니다. 노바시네마에 가입되지 않은 회원입니다.\n" +
                            "먼저 노바시네마 앱에서 회원가입을 진행해주세요.";
                }

                session.reset();
                session.setFlowType(UserSession.FlowType.MOVIE);
                yield bookingHandler.handle(userInput, session, userId);
            }
            case LOOKUP_BOOKING -> lookupHandler.handle(userInput, userId);
            case CANCEL_BOOKING -> cancelHandler.handle(userInput, userId);
            default -> "죄송합니다. 이해하지 못했어요. 다시 말씀해 주세요.";
        };
    }
}
