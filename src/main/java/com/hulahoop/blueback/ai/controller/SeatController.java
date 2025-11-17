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

    /** ✅ 스케줄별 좌석 조회 API (로그인 필수) */
    @GetMapping("/seats")
    public ResponseEntity<?> getSeats(
            @RequestParam int scheduleNum,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("❌ 로그인 후 이용 가능합니다.");
        }

        Map<String, Object> res = intentService.processIntent(
                "movie_booking_step3",
                Map.of("scheduleNum", scheduleNum)
        );

        List<Map<String, Object>> seats = (List<Map<String, Object>>) res.get("seats");

        //
        List<Map<String, Object>> result = seats.stream().map(seat -> Map.of(
                "seat_code", seat.get("seat_code"),
                "row_label", seat.get("row_label"),
                "col_num", seat.get("col_num"),
                "is_aisle", seat.get("is_aisle"),
                "reserved", seat.get("reserved")
        )).toList();

        return ResponseEntity.ok(result);
    }

    /** ✅ 좌석 예약 API (로그인 필수) */
    @PostMapping("/book-seat")
    public ResponseEntity<?> bookSeat(
            @RequestBody Map<String, Object> req,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body("❌ 로그인 후 이용 가능합니다.");
        }

        Integer scheduleNum = (Integer) req.get("scheduleNum");
        Integer seatCode = (Integer) req.get("seatCode");

        if (scheduleNum == null || seatCode == null) {
            return ResponseEntity.badRequest().body("scheduleNum & seatCode is required");
        }

        // ✅ IntentService 통해 좌석 HOLD
        intentService.processIntent("movie_booking_step4", Map.of(
                "scheduleNum", scheduleNum,
                "seatCode", seatCode
        ));

        // ✅ Gateway(8080)에게 좌석 업데이트 알림 전송 (REST 방식)
        try {
            com.hulahoop.blueback.ai.utils.HttpClient.post(
                    "http://localhost:8080/internal/seat-updated",
                    Map.of("scheduleNum", scheduleNum)
            );
        } catch (Exception e) {
            System.err.println("⚠️ Gateway 좌석 업데이트 알림 실패: " + e.getMessage());
        }

        return ResponseEntity.ok("✅ 좌석 예약 성공 (HOLD)");
    }
}
