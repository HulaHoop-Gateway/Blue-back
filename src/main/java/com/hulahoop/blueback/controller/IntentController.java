package com.hulahoop.blueback.controller;

import com.hulahoop.blueback.service.IntentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/intent")
public class IntentController {

    private final IntentService intentService;

    public IntentController(IntentService intentService) {
        this.intentService = intentService;
    }

    // AI가 intent를 감지했을 때 게이트웨이로 전달
    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> handleIntent(@RequestBody Map<String, Object> payload) {
        String intent = (String) payload.get("intent");
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        Map<String, Object> result = intentService.processIntent(intent, data);
        return ResponseEntity.ok(result);
    }
}
