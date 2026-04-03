package com.hulahoop.blueback.history.controller;

import com.hulahoop.blueback.history.model.dto.HistoryResponseDto;
import com.hulahoop.blueback.history.model.service.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// 프론트엔드 마이페이지의 '사용 내역(결제/예약 현황)' 화면에서 호출하는 API들을 모아놓은 컨트롤러
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    // 특정 회원의 결제 내역이나 예약 내역을 몽땅 조회하는 API
    // 예를 들어 /api/history/U000000001?status=SUCCESS 이렇게 부르면 U000000001 회원의 성공 내역만 쫙
    // 뽑아줌
    // status 파라미터는 필수가 아니게(required=false) 열어둬서 안 보내면 걍 다 주도록 설계함
    @GetMapping("/{memberCode}")
    public ResponseEntity<List<HistoryResponseDto>> getHistoryByMemberCode(
            @PathVariable String memberCode,
            @RequestParam(required = false) String status) {

        log.info("API 호출됨: memberCode={}, status={}", memberCode, status);

        List<HistoryResponseDto> history = historyService.getTransactionHistory(memberCode, status);

        // 내역이 비어있으면 200 OK 빈배열을 내려줘도 되지만,
        // 204 No Content를 명시적으로 내려서 "결과는 정상인데 줄 데이터가 없다"는 걸 프론트에 확실히 알려줌
        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(history);
    }

    // 마이페이지 결제 내역 화면에서 [취소하기] 버튼을 눌렀을 때 호출되는 API
    // GET이나 DELETE가 아니라 PUT을 쓴 이유는, 진짜 DB에서 내역을 삭제하는 게 아니라
    // 상태값(status)을 환불(R)이나 에러(E)로 '수정(Update)'하는 개념이기 때문임
    @PutMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelReservation(
            @RequestBody com.hulahoop.blueback.history.model.dto.CancellationRequest request) {

        // 프론트에서 넘어온 고유 거래번호(transactionNum) 확인
        log.info("취소 요청: transactionNum={}", request.getTransactionNum());

        // 취소가 성공했는지, 실패했으면 왜 실패했는지 메시지를 Map으로 감싸서 받아옴
        Map<String, Object> result = historyService.cancelReservation(request.getTransactionNum());

        // 서비스단에서 success 키에 boolean 값을 넣어줬는지 확인하고 분기 처리
        boolean success = (boolean) result.getOrDefault("success", false);
        if (success) {
            // 성공시 200 상태코드
            return ResponseEntity.ok(result);
        } else {
            // 실패시 400 상태코드(Bad Request)로 던져서 프론트의 catch 블록으로 빠지게 유도함
            return ResponseEntity.badRequest().body(result);
        }
    }
}