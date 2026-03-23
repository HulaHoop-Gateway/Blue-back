package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.service.IntentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/movies")
public class SeatController {

    private final IntentService intentService;

    @Autowired
    public SeatController(IntentService intentService) {
        this.intentService = intentService;
    }

    // 특정 스케줄의 좌석 목록 조회 - scheduleNum으로 해당 상영 시간대의 좌석 정보를 가져옴
    @GetMapping("/seats")
    public ResponseEntity<?> getSeats(
            @RequestParam int scheduleNum,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("로그인 후 이용 가능합니다.");
        }

        Map<String, Object> res = intentService.processIntent(
                "movie_booking_step3",
                Map.of("scheduleNum", scheduleNum));

        List<Map<String, Object>> seats = (List<Map<String, Object>>) res.get("seats");

        // 클라이언트에 필요한 필드만 골라서 반환
        List<Map<String, Object>> result = seats.stream().map(seat -> Map.of(
                "seat_code", seat.get("seat_code"),
                "row_label", seat.get("row_label"),
                "col_num", seat.get("col_num"),
                "is_aisle", seat.get("is_aisle"),
                "reserved", seat.get("reserved"))).toList();

        return ResponseEntity.ok(result);
    }

    // 좌석 예약 처리 - HOLD 상태로 먼저 잡아두고, 게이트웨이에 변경 사실을 알림
    @PostMapping("/book-seat")
    public ResponseEntity<?> bookSeat(
            @RequestBody Map<String, Object> req,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("로그인 후 이용 가능합니다.");
        }

        Integer scheduleNum = (Integer) req.get("scheduleNum");
        Integer seatCode = (Integer) req.get("seatCode");

        if (scheduleNum == null || seatCode == null) {
            return ResponseEntity.badRequest().body("scheduleNum & seatCode is required");
        }

        // IntentService를 통해 좌석을 HOLD 상태로 변경
        intentService.processIntent("movie_booking_step4", Map.of(
                "scheduleNum", scheduleNum,
                "seatCode", seatCode));

        // 게이트웨이(8080)에 좌석 상태 변경을 알려서 다른 서비스에서도 최신 상태를 볼 수 있게 함
        try {
            com.hulahoop.blueback.ai.utils.HttpClient.post(
                    "http://localhost:8080/internal/seat-updated",
                    Map.of("scheduleNum", scheduleNum));
        } catch (Exception e) {
            System.err.println("Gateway 좌석 업데이트 알림 실패: " + e.getMessage());
        }

        return ResponseEntity.ok("좌석 예약 성공 (HOLD)");
    }
}
