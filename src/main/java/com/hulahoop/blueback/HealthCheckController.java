package com.hulahoop.blueback;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ✅ /health 엔드포인트: AI/Frontend 서버 (8090) 헬스체크
 */
@RestController
public class HealthCheckController {

    @Value("${server.port}")
    private int port;

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("server", "AI/Frontend Service");
        status.put("port", port);
        status.put("timestamp", LocalDateTime.now().toString());
        return status;
    }
}

