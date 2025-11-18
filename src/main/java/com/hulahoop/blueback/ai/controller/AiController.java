package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.dto.AiResponseDTO;
import com.hulahoop.blueback.ai.model.service.GeminiService;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<?> ask(
            @RequestBody Map<String, String> request,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        String message = request.get("message");
        String userId = principal.getName();

        AiResponseDTO response = geminiService.askGemini(message, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 🧹 세션 초기화
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetConversation(Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        String userId = principal.getName();
        geminiService.resetConversation(userId);

        return ResponseEntity.ok(Map.of("message", "reset ok"));
    }

    /**
     * 🎬 좌석 선택 완료 → GeminiService 경유로 호출
     */
    @PostMapping("/complete-seat")
    public ResponseEntity<?> completeSeat(Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        String userId = principal.getName();

        return ResponseEntity.ok(Map.of("message", "test"));
    }
}
