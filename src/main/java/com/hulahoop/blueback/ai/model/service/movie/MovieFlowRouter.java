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

    // ⭐⭐⭐ 추가됨: GeminiService에서 사용하기 위해 CancelFlow 여부 체크 함수 제공
    public boolean isInCancelFlow(String userId) {
        return cancelHandler.isInCancelFlow(userId);
    }
    // ⭐⭐⭐ 여기까지 추가


    public String handle(String userInput, UserSession session, String userId) {

        // 0️⃣ 취소 플로우 중이면 무조건 취소 handler로 라우팅
        if (cancelHandler.isInCancelFlow(userId)) {
            return cancelHandler.handle(userInput, userId);
        }

        MovieIntentResolver.MovieIntent intent = intentResolver.resolve(userInput);

        System.out.println("🧭 현재 상태: " + session.getStep());
        System.out.println("🎯 해석된 intent: " + intent);

        // 1️⃣ 예매 흐름 도중 START_BOOKING 명령 → 예매 재시작
        if (session.getStep() != UserSession.Step.IDLE) {

            if (intent == MovieIntentResolver.MovieIntent.START_BOOKING) {
                System.out.println("🔄 예매 재시작 요청 감지 → 세션 초기화");
                session.reset();
                return bookingHandler.handle(userInput, session, userId);
            }

            // 예매 흐름 계속 유지
            System.out.println("🔄 예매 흐름 중간 단계 유지: " + session.getStep());
            return bookingHandler.handle(userInput, session, userId);
        }

        // 2️⃣ IDLE 상태 → Intent에 따라 분기
        return switch (intent) {
            case START_BOOKING -> {
                session.reset();
                yield bookingHandler.handle(userInput, session, userId);
            }
            case LOOKUP_BOOKING -> lookupHandler.handle(userInput, userId);
            case CANCEL_BOOKING -> cancelHandler.handle(userInput, userId);
            default -> "❓ 죄송합니다. 이해하지 못했어요. 다시 말씀해 주세요.";
        };
    }
}
