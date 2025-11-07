package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.service.IntentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/intent")
public class IntentController {

    private static final Logger log = LoggerFactory.getLogger(IntentController.class);

    private final IntentService intentService;

    public IntentController(IntentService intentService) {
        this.intentService = intentService;
    }

    // AI가 intent를 감지했을 때 게이트웨이로 전달
    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> handleIntent(@RequestBody Map<String, Object> payload) {
        String intent = (String) payload.get("intent");
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        log.info("intent: {}", intent);
        log.info("data: {}", data);

        Map<String, Object> result = intentService.processIntent(intent, data);
        return ResponseEntity.ok(result);
    }
}
