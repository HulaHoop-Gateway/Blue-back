package com.hulahoop.blueback.history.controller;

import com.hulahoop.blueback.history.model.dto.HistoryResponseDto;
import com.hulahoop.blueback.history.model.service.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{memberCode}")
    public ResponseEntity<List<HistoryResponseDto>> getHistoryByMemberCode(@PathVariable String memberCode, @RequestParam(required = false) String status) {
        log.info("API 호출됨: memberCode={}, status={}", memberCode, status); // Added log statement
        List<HistoryResponseDto> history = historyService.getTransactionHistory(memberCode, status);
        if (history.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(history);
    }
}