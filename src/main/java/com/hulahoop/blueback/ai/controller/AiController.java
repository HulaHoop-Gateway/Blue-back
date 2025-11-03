// src/main/java/com/hulahoop/blueback/ai/controller/AiController.java
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

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> ask(
            @RequestBody Map<String, String> request,
            Principal principal
    ) {
        String message = request.get("message");
        // principal should not be null if security is configured and request is authenticated
        String userId = (principal != null) ? principal.getName() : "guest";

        String response = geminiService.askGemini(message, userId);
        return ResponseEntity.ok(Map.of("message", response));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetConversation(Principal principal) {
        String userId = (principal != null) ? principal.getName() : "guest";
        geminiService.resetConversation(userId);
        return ResponseEntity.ok(Map.of("message", "reset ok"));
    }
}
