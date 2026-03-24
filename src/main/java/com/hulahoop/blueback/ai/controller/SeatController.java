package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.service.IntentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

// 프론트엔드의 영화 좌석 선택 모달(커스텀 UI)과 백엔드가 통신하기 위해 만들어진 전용 컨트롤러
@RestController
@RequestMapping("/api/movies")
public class SeatController {

    private final IntentService intentService;

    @Autowired
    public SeatController(IntentService intentService) {
        this.intentService = intentService;
    }

    // 사용자가 영화 상영 시간을 선택했을 때, 프론트 모달창에서 좌석 배치도를 그리기 위해 좌석 데이터를 요청하는 API
    @GetMapping("/seats")
    public ResponseEntity<?> getSeats(
            @RequestParam int scheduleNum, // 상영 스케줄 고유 번호
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("로그인 후 이용 가능합니다.");
        }

        // 영화 서버(NovaCinema)의 DB를 직접 찌를 수 없으니 MSA 구조에 맞춰 IntentService를 태워 게이트웨이를 통해 요청함
        // "movie_booking_step3" 라는 인텐트를 날리면 영화 서버의 해당 로직이 좌석 목록을 통째로 넘겨줌
        Map<String, Object> res = intentService.processIntent(
                "movie_booking_step3",
                Map.of("scheduleNum", scheduleNum));

        List<Map<String, Object>> seats = (List<Map<String, Object>>) res.get("seats");

        // 영화 서버에서 온 거대한 데이터 중, 클라이언트(리액트 등 프론트)가 좌석을 렌더링하는데 꼭 필요한 속성 5개만 정제(매핑)해서 담아줌
        // 이렇게 해야 응답 페이로드도 가벼워지고 프론트 개발자가 데이터 까보기 편함
        List<Map<String, Object>> result = seats.stream().map(seat -> Map.of(
                "seat_code", seat.get("seat_code"), // 좌석 PK (나중에 예약할 때 넘길 값)
                "row_label", seat.get("row_label"), // 행 (A, B, C...)
                "col_num", seat.get("col_num"), // 열 (1, 2, 3...)
                "is_aisle", seat.get("is_aisle"), // 복도 여부 (ui 띄울때 띄어쓰기용)
                "reserved", seat.get("reserved"))).toList(); // 이미 누가 예약한(혹은 홀딩한) 좌석인지

        return ResponseEntity.ok(result);
    }

    // 프론트 모달에서 빈 좌석을 클릭했을 때 임시로 '잡아두기(HOLD)' 처리하는 API
    // 극장 예매 특성상 내가 자리를 고르는 사이 남이 먼저 선점하면 곤란하니까 결제 전까지 임시 홀딩하는 메커니즘임
    @PostMapping("/book-seat")
    public ResponseEntity<?> bookSeat(
            @RequestBody Map<String, Object> req,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("로그인 후 이용 가능합니다.");
        }

        Integer scheduleNum = (Integer) req.get("scheduleNum");
        Integer seatCode = (Integer) req.get("seatCode");

        // 필수 파라미터 유효성 검사
        if (scheduleNum == null || seatCode == null) {
            return ResponseEntity.badRequest().body("scheduleNum & seatCode is required");
        }

        // 1차: IntentService를 통해 영화 서버 측에 "이 스케줄의 이 좌석을 홀드 상태로 바꿔달라"고 지시
        intentService.processIntent("movie_booking_step4", Map.of(
                "scheduleNum", scheduleNum,
                "seatCode", seatCode));

        // 2차: 게이트웨이의 웹소켓/Event 채널 쪽에 "좌석 상태가 업데이트 됨" 이벤트를 브로드캐스팅(전파)함
        // 이걸 받은 게이트웨이가 다른 사용자 프론트 화면에도 좌석 현황을 실시간(SSE 방식 등)으로 그려줄 수 있게 트리거 역할을 하는 것
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
