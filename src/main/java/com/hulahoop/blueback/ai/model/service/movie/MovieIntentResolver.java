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

        /** 🔒 1) 숫자(예매번호) 입력은 절대 CANCEL_BOOKING 아님 */
        // 예매번호는 항상 10자리 숫자를 사용하므로 해당 입력은 취소/조회 Intent로 분리되면 안 됨
        if (input.matches("^\\d{10}$")) {
            return MovieIntent.UNKNOWN;
        }

        /** 🎬 2) 예매 시작 Intent */
        if (
                (input.contains("영화") && input.contains("예매")) ||
                        (input.contains("영화") && input.contains("예약")) ||
                        input.contains("영화 예매")
        ) {
            System.out.println("예매");
            return MovieIntent.START_BOOKING;
        }

        /** ❌ 3) 예매 취소 Intent */
        if (
                (input.contains("예매") && input.contains("취소")) ||   // 예매 취소
                        (input.contains("예약") && input.contains("취소")) ||   // 예약 취소
                        input.contains("예매 취소") ||
                        input.contains("예약 취소") ||
                        input.matches("^(2번|2)$")                              // 메뉴에서 2번
        ) {
            System.out.println("취소");
            return MovieIntent.CANCEL_BOOKING;
        }

        /** 🔍 4) 예매 조회 Intent */
        if (
                input.contains("내 예매") ||
                        input.contains("예매 확인") ||
                        input.contains("예약 확인") ||
                        input.matches("^(1번|1)$")     // 메뉴에서 1번
        ) {
            System.out.println("조회");
            return MovieIntent.LOOKUP_BOOKING;
        }

        /** 🎥 5) 상영 정보 조회 Intent */
        if (
                input.contains("상영") ||
                        input.contains("시간표") ||
                        input.contains("스케줄")
        ) {
            return MovieIntent.SHOW_MOVIES;
        }

        /** ❔ 6) 그 외 → UNKNOWN */
        return MovieIntent.UNKNOWN;
    }
}
