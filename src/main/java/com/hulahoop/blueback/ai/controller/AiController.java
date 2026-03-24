package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.dto.AiResponseDTO;
import com.hulahoop.blueback.ai.model.service.GeminiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

// 사용자가 프론트엔드 채팅창에서 입력한 메시지(예매해줘, 취소해줘 등)를 가장 처음으로 받아내는 AI 진입 컨트롤러
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // 메인 채팅 수신 API
    // 프론트엔드에서 {"message": "영화 예약할래"} 형태로 쏘면 여기로 들어옴
    // Principal은 스프링 시큐리티가 지원하는 기능인데, 이전에 JwtFilter가 토큰을 까서 인증 객체를 만들어뒀기 때문에
    // 컨트롤러 단에서는 그냥 매개변수로 Principal만 달아놓으면 알아서 현재 로그인된 회원의 아이디(username)가 쏙 들어옴
    @PostMapping("/ask")
    public ResponseEntity<?> ask(
            @RequestBody Map<String, String> request,
            Principal principal) {

        // 토큰이 없거나 잘못돼서 필터에서 튕기지 않고 백엔드 로직에 이상하게 접근했을 경우를 대비한 2차 안전장치
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        // 사용자가 보낸 채팅 내용 추출
        String message = request.get("message");

        // 인증된 사용자 아이디 추출 (내부적으로 서비스의 세션 식별키로 쓰임)
        String userId = principal.getName();

        // 가장 무거운 작업을 하는 GeminiService한테 메시지 분석과 다음 응답 생성을 통째로 맡김
        AiResponseDTO response = geminiService.askGemini(message, userId);

        // 결과로 나온 대답(일반 텍스트일수도, 버튼이나 UI 옵션이 섞여있을 수도 있음)을 반환함
        return ResponseEntity.ok(response);
    }

    // 대화 초기화 API
    // 사용자가 엉뚱한 대답을 해서 처음부터 예약을 다시 시작하고 싶거나, 화면을 아예 새로고침 했을 때 호출됨
    // 이거 안 하면 이전 단계(예: "몇 명인가요?" 라고 물어본 상태)에 계속 멈춰있어서 꼬이게 됨
    @PostMapping("/reset")
    public ResponseEntity<?> resetConversation(Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        String userId = principal.getName();

        // 서비스 단에 "이 사람 세션 정보 다 날려버려라"고 지시
        geminiService.resetConversation(userId);

        return ResponseEntity.ok(Map.of("message", "reset ok"));
    }

    // 프론트 모달창에서 좌석을 다 골랐을 때 상태를 처리해주려고 뚫어둔 엔드포인트
    // (현재는 추가 로직 확장을 위해 틀만 잡아둔 상태임)
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
