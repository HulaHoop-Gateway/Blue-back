package com.hulahoop.blueback.ai.model.service.movie;

import org.springframework.stereotype.Component;

@Component
public class MovieIntentResolver {

    public enum MovieIntent {
        START_BOOKING, // 영화 예매 시작
        SHOW_MOVIES, // 상영 영화/스케줄 조회
        CANCEL_BOOKING, // 예매 취소
        LOOKUP_BOOKING, // 내 예매 조회
        UNKNOWN // 알 수 없는 입력
    }

    public MovieIntent resolve(String input) {
        if (input == null || input.isBlank())
            return MovieIntent.UNKNOWN;

        input = input.toLowerCase().trim();

        // 예매번호는 10자리 숫자 형식 - 이게 들어오면 취소/조회 Intent로 잘못 분류되면 안 됨
        if (input.matches("^\\d{10}$")) {
            return MovieIntent.UNKNOWN;
        }

        // 예매 시작 Intent - "영화 예매", "영화 예약" 같은 표현
        if ((input.contains("영화") && input.contains("예매")) ||
                (input.contains("영화") && input.contains("예약")) ||
                input.contains("영화 예매")) {
            System.out.println("예매");
            return MovieIntent.START_BOOKING;
        }

        // 예매 취소 Intent - "예매 취소", "예약 취소" 또는 메뉴에서 2번 선택
        if ((input.contains("예매") && input.contains("취소")) ||
                (input.contains("예약") && input.contains("취소")) ||
                input.contains("예매 취소") ||
                input.contains("예약 취소") ||
                input.matches("^(2번|2)$")) {
            System.out.println("취소");
            return MovieIntent.CANCEL_BOOKING;
        }

        // 예매 조회 Intent - "내 예매", "예매 확인" 또는 메뉴에서 1번 선택
        if (input.contains("내 예매") ||
                input.contains("예매 확인") ||
                input.contains("예약 확인") ||
                input.matches("^(1번|1)$")) {
            System.out.println("조회");
            return MovieIntent.LOOKUP_BOOKING;
        }

        // 상영 정보 조회 Intent
        if (input.contains("상영") ||
                input.contains("시간표") ||
                input.contains("스케줄")) {
            return MovieIntent.SHOW_MOVIES;
        }

        return MovieIntent.UNKNOWN;
    }
}
