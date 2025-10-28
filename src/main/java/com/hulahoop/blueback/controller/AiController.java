package com.hulahoop.blueback.controller;

import com.hulahoop.blueback.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askAI(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String response = geminiService.askGemini(message);
        return ResponseEntity.ok(Map.of("message", response));
    }

    // 대화 리셋용 (원할 때 프론트에서 newChat 시 호출)
    @PostMapping("/reset")
    public ResponseEntity<Void> resetConversation() {
        geminiService.resetConversation();
        return ResponseEntity.ok().build();
    }
}
