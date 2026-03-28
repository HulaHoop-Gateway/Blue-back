package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.service.IntentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

// MSA 환경에서 의도(Intent) 분석 결과를 기반으로 타 마이크로서비스(영화관, 자전거 등)와 통신할 때 거쳐가는 컨트롤러
@RestController
@RequestMapping("/api/intent")
public class IntentController {

    private static final Logger log = LoggerFactory.getLogger(IntentController.class);

    private final IntentService intentService;

    public IntentController(IntentService intentService) {
        this.intentService = intentService;
    }

    // 사용자의 자연어 명령을 AI가 분석해서 "아, 이건 자전거 목록을 달라는 거네!(bike_list)" 처럼 의도가 특정되었을 때
    // 프론트나 챗봇 엔진 내부에서 이 API를 찔러 데이터를 요청하게 됨
    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> handleIntent(@RequestBody Map<String, Object> payload) {

        // 어떤 서비스의 어떤 기능을 원하는지 구분하는 '의도' (예: movie_booking_step1)
        String intent = (String) payload.get("intent");

        // 그 의도를 실행하기 위해 필요한 파라미터들 (예: 예약할 지점명, 상영날짜 등)
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        log.info("intent: {}", intent);
        log.info("data: {}", data);

        // IntentService 객체를 통해 Spring Cloud Gateway(8080 포트)로 직접 요청을 쏨
        // 거기로 쏘면 게이트웨이가 intent 값을 보고 "아 이건 노바시네마 서버로", "이건 자전거 서버로" 라우팅해주는 구조임
        Map<String, Object> result = intentService.processIntent(intent, data);

        return ResponseEntity.ok(result);
    }
}
