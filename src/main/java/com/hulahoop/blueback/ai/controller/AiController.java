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

    // 사용자가 채팅창에서 메시지를 보내면 여기로 들어옴
    // Principal은 JwtFilter에서 SecurityContext에 넣어준 인증 정보를 스프링이 자동으로 주입해줌
    @PostMapping("/ask")
    public ResponseEntity<?> ask(
            @RequestBody Map<String, String> request,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        String message = request.get("message");
        String userId = principal.getName();

        AiResponseDTO response = geminiService.askGemini(message, userId);
        return ResponseEntity.ok(response);
    }

    // 대화 세션 초기화 - 새 대화 시작하거나 이전 맥락 지울 때 호출
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

    // 좌석 선택 완료 후 후처리 - 현재는 테스트용 응답만 반환
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
