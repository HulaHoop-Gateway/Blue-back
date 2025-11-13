package com.hulahoop.blueback.ai.model.service.movie;

import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.stereotype.Component;

@Component
public class MovieFlowRouter {

    private final MovieIntentResolver intentResolver;
    private final MovieBookingFlowHandler bookingHandler;
    private final MovieLookUpHandler lookupHandler;
    private final MovieCancelHandler cancelHandler;

    public MovieFlowRouter(MovieIntentResolver intentResolver,
                           MovieBookingFlowHandler bookingHandler,
                           MovieLookUpHandler lookupHandler,
                           MovieCancelHandler cancelHandler) {
        this.intentResolver = intentResolver;
        this.bookingHandler = bookingHandler;
        this.lookupHandler = lookupHandler;
        this.cancelHandler = cancelHandler;
    }

    public String handle(String userInput, UserSession session, String userId) {
        // 예매 흐름 중간이면 무조건 예매 핸들러로 위임
        if (session.getStep() != UserSession.Step.IDLE) {
            System.out.println("🔄 예매 흐름 중간 단계: " + session.getStep());
            return bookingHandler.handle(userInput, session, userId);
        }

        // IDLE 상태일 때만 intent 분기
        MovieIntentResolver.MovieIntent intent = intentResolver.resolve(userInput);

        // ✅ 디버깅 로그 출력
        System.out.println("🧭 현재 상태: " + session.getStep());
        System.out.println("🎯 해석된 intent: " + intent);

        return switch (intent) {
            case START_BOOKING -> bookingHandler.handle(userInput, session, userId);
            case LOOKUP_BOOKING -> lookupHandler.handle(userInput, userId);
            case CANCEL_BOOKING -> cancelHandler.handle(userInput, userId);
            default -> "❓ 죄송합니다. 이해하지 못했어요. 다시 말씀해 주세요.";
        };
    }
}
