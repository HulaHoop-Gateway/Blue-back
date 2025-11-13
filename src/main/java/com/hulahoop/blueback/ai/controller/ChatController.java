package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private GeminiService geminiService;

    /**
     * 좌석 선택 완료 → 세션 초기화 및 안내 메시지 반환
//     */
//    @PostMapping("/complete-seat")
//    public ResponseEntity<String> completeSeat(Principal principal) {
//        String userId = (principal != null) ? principal.getName() : "guest";
////        String message = geminiService.completeSeatSelection(userId);
//        return ResponseEntity.ok(message != null ? message : "처리 중 오류가 발생했습니다.");
//    }

}
