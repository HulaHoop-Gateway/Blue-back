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

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{memberCode}")
    public ResponseEntity<List<HistoryResponseDto>> getHistoryByMemberCode(@PathVariable String memberCode,
            @RequestParam(required = false) String status) {
        log.info("API 호출됨: memberCode={}, status={}", memberCode, status); // Added log statement
        List<HistoryResponseDto> history = historyService.getTransactionHistory(memberCode, status);
        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(history);
    }

    @PutMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelReservation(
            @RequestBody com.hulahoop.blueback.history.model.dto.CancellationRequest request) {
        log.info("취소 요청: transactionNum={}", request.getTransactionNum());
        Map<String, Object> result = historyService.cancelReservation(request.getTransactionNum());

        boolean success = (boolean) result.getOrDefault("success", false);
        if (success) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}