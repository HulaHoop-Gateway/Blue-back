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

    @PostMapping("/complete-seat")
    public ResponseEntity<Void> completeSeat(Principal principal) {
        String userId = principal.getName();
        geminiService.completeSeatSelection(userId);
        return ResponseEntity.ok().build();
    }
}
