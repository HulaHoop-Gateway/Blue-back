package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    /**
     * 💬 AI 대화 요청
     */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> ask(
            @RequestBody Map<String, String> request,
            Principal principal
    ) {
        String message = request.get("message");
        String userId = (principal != null) ? principal.getName() : "guest";
        String response = geminiService.askGemini(message, userId);
        return ResponseEntity.ok(Map.of("message", response));
    }

    /**
     * 🧹 세션 초기화
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetConversation(Principal principal) {
        String userId = (principal != null) ? principal.getName() : "guest";
        geminiService.resetConversation(userId);
        return ResponseEntity.ok(Map.of("message", "reset ok"));
    }

    /**
     * 🎬 좌석 선택 완료 → GeminiService 경유로 호출
     */
    @PostMapping("/complete-seat")
    public ResponseEntity<Map<String, String>> completeSeat(Principal principal) {
        String userId = (principal != null) ? principal.getName() : "guest";
        String result = geminiService.completeSeatSelection(userId);
        return ResponseEntity.ok(Map.of("message", result));
    }
}
