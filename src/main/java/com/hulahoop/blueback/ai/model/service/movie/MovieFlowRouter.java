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

        if (cancelHandler.isInCancelFlow(userId)) {
            return cancelHandler.handle(userInput, userId);
        }

        MovieIntentResolver.MovieIntent intent = intentResolver.resolve(userInput);

        System.out.println("🧭 현재 상태: " + session.getStep());
        System.out.println("🎯 해석된 intent: " + intent);

        // ✅ 예매 도중 "예매 시작" 요청 → 재시작
        if (session.getStep() != UserSession.Step.IDLE) {

            if (intent == MovieIntentResolver.MovieIntent.START_BOOKING) {
                System.out.println("🔄 예매 재시작 요청 감지 → 세션 초기화");

                // 🔹 영화관 회원 검증
                String phoneNumber = membershipVerificationService.getUserPhoneNumber(userId);
                if (phoneNumber == null) {
                    return "회원 정보를 찾을 수 없습니다.";
                }

                if (!membershipVerificationService.isCinemaMember(phoneNumber)) {
                    return "❌ 죄송합니다. 노바시네마에 가입되지 않은 회원입니다.\n" +
                            "먼저 노바시네마 앱에서 회원가입을 진행해주세요.";
                }

                session.reset();
                session.setFlowType(UserSession.FlowType.MOVIE); // ✅ 추가됨
                return bookingHandler.handle(userInput, session, userId);
            }

            System.out.println("🔄 예매 흐름 유지: " + session.getStep());
            return bookingHandler.handle(userInput, session, userId);
        }

        // ✅ IDLE 상태에서 예매 시작
        return switch (intent) {
            case START_BOOKING -> {
                // 🔹 영화관 회원 검증
                String phoneNumber = membershipVerificationService.getUserPhoneNumber(userId);
                if (phoneNumber == null) {
                    yield "회원 정보를 찾을 수 없습니다.";
                }

                if (!membershipVerificationService.isCinemaMember(phoneNumber)) {
                    yield "❌ 죄송합니다. 노바시네마에 가입되지 않은 회원입니다.\n" +
                            "먼저 노바시네마 앱에서 회원가입을 진행해주세요.";
                }

                session.reset();
                session.setFlowType(UserSession.FlowType.MOVIE); // ✅ 추가됨
                yield bookingHandler.handle(userInput, session, userId);
            }
            case LOOKUP_BOOKING -> lookupHandler.handle(userInput, userId);
            case CANCEL_BOOKING -> cancelHandler.handle(userInput, userId);
            default -> "❓ 죄송합니다. 이해하지 못했어요. 다시 말씀해 주세요.";
        };
    }
}
