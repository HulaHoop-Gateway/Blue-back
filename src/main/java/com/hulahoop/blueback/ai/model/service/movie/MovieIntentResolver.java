package com.hulahoop.blueback.ai.model.service.movie;

import org.springframework.stereotype.Component;

@Component
public class MovieIntentResolver {

    public enum MovieIntent {
        START_BOOKING,     // 영화 예매 시작
        SHOW_MOVIES,       // 상영 영화/스케줄 조회
        CANCEL_BOOKING,    // 예매 취소
        LOOKUP_BOOKING,    // 내 예매 조회
        UNKNOWN            // 알 수 없는 입력
    }

    public MovieIntent resolve(String input) {
        if (input == null || input.isBlank()) return MovieIntent.UNKNOWN;
        input = input.toLowerCase().trim();


        if ((input.contains("영화") && input.contains("예매")) || (input.contains("영화") && input.contains("예약"))) {
            System.out.println("예매");
            return MovieIntent.START_BOOKING;
        }

        if (input.contains("영화") && input.contains("취소")||(input.contains("예매") && input.contains("취소"))) {
            System.out.println("취소");
            return MovieIntent.CANCEL_BOOKING;
        }


        if (input.contains("내 예매") || input.contains("예매 확인") || input.contains("예약 확인")) {
            System.out.println("조회");
            return MovieIntent.LOOKUP_BOOKING;
        }


        if (input.contains("상영") || input.contains("시간표") || input.contains("스케줄")) {
            return MovieIntent.SHOW_MOVIES;
        }

        return MovieIntent.UNKNOWN;
    }
}
