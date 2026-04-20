package com.hulahoop.blueback.ai.controller;

import com.hulahoop.blueback.ai.model.dto.AiResponseDTO;
import com.hulahoop.blueback.ai.model.service.GeminiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

// 사용자가 프론트엔드 채팅창에서 입력한 메시지(예매해줘, 취소해줘 등)를 가장 처음으로 받아내는 AI 진입 컨트롤러
// @RestController는 @Controller + @ResponseBody가 합쳐진 건데, 메서드 반환값이 자동으로 JSON으로 변환되어 응답 바디에 들어감
// @RequestMapping("/api/ai")는 이 클래스 안에 있는 모든 API 경로 앞에 /api/ai를 공통으로 붙여주는 역할
@RestController
@RequestMapping("/api/ai")
public class AiController {

    // GeminiService가 AI 관련 실제 로직을 전부 처리하고, 이 컨트롤러는 요청을 받아서 넘겨주는 역할만 함
    // final로 선언해서 한번 주입받은 참조값이 바뀌지 못하도록 고정해둔 것
    private final GeminiService geminiService;

    // 생성자 주입 방식 - @Autowired를 안 써도 생성자가 하나면 스프링이 알아서 주입해줌
    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // 메인 채팅 수신 API
    // 프론트엔드에서 {"message": "영화 예약할래"} 형태로 쏘면 여기로 들어옴
    // Principal은 Java 표준 인터페이스인데, 스프링 시큐리티가 JwtFilter에서 토큰을 검증한 다음
    // 인증된 사용자 정보를 자동으로 Principal 객체에 담아서 주입해주는 방식
    // 로그인된 사용자가 요청하면 → 사용자 아이디가 담긴 Principal이 들어오고
    // 로그인 안 한 사용자가 요청하면 → null이 들어옴
    // 그래서 컨트롤러 단에서는 그냥 매개변수로 Principal만 달아놓으면 알아서 현재 로그인된 회원의 아이디(username)가 쏙 들어옴
    @PostMapping("/ask")
    // ResponseEntity<?>에서 <>안에 타입을 지정하면 응답 바디 타입이 고정되는데
    // 이 메서드는 실패하면 Map(에러 메시지), 성공하면 AiResponseDTO가 나와서 타입이 달라짐
    // ResponseEntity<String>이라고 쓰면 항상 String만 반환할 수 있고
    // ResponseEntity<AiResponseDTO>라고 쓰면 항상 그 DTO만 반환할 수 있는데
    // 상황마다 다른 타입을 반환해야 하니까 <?> 로 열어두는 수밖에 없는 것
    // @RequestBody는 프론트에서 보낸 JSON 바디를 Map으로 변환해서 받는 역할
    public ResponseEntity<?> ask(
            @RequestBody Map<String, String> request,
            Principal principal) {

        // 토큰이 없거나 잘못돼서 필터에서 튕기지 않고 백엔드 로직에 이상하게 접근했을 경우를 대비한 2차 안전장치
        // principal이 null이면 401 UNAUTHORIZED 상태코드와 함께 에러 메시지를 JSON으로 반환
        // Map.of("key", "value")는 Java 9부터 생긴 불변 Map 생성 단축 문법으로
        // new HashMap<>() 만들고 put()으로 값 넣는 걸 한 줄로 줄인 것과 같음
        // 스프링이 이 Map을 자동으로 {"error": "로그인이 필요한 서비스입니다."} JSON으로 변환해서 응답으로 내려보냄
        // 프론트에서는 response.data.error 로 꺼내서 쓸 수 있는 구조
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        // 사용자가 보낸 채팅 내용 추출
        String message = request.get("message");

        // principal.getName()은 스프링 시큐리티가 JWT 토큰에서 꺼낸 사용자 식별자(보통 로그인 아이디)를 리턴함
        // 인증된 사용자 아이디 추출 (내부적으로 서비스의 세션 식별키로 쓰임)
        String userId = principal.getName();

        // 가장 무거운 작업을 하는 GeminiService한테 메시지 분석과 다음 응답 생성을 통째로 맡김
        AiResponseDTO response = geminiService.askGemini(message, userId);

        // 결과로 나온 대답(일반 텍스트일수도, 버튼이나 UI 옵션이 섞여있을 수도 있음)을 반환함
        // ResponseEntity.ok()는 HTTP 200 상태코드와 함께 응답 바디를 같이 실어 보내는 것
        return ResponseEntity.ok(response);
    }

    // 대화 초기화 API
    // 사용자가 엉뚱한 대답을 해서 처음부터 예약을 다시 시작하고 싶거나, 화면을 아예 새로고침 했을 때 호출됨
    // 이거 안 하면 이전 단계(예: "몇 명인가요?" 라고 물어본 상태)에 계속 멈춰있어서 꼬이게 됨
    @PostMapping("/reset")
    public ResponseEntity<?> resetConversation(Principal principal) {

        // ask()랑 동일하게 로그인 여부 한 번 더 체크
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        // 어느 사용자의 세션을 초기화할지 특정하기 위해 아이디 꺼내오는 것
        String userId = principal.getName();

        // 서비스 단에 "이 사람 세션 정보 다 날려버려라"고 지시
        geminiService.resetConversation(userId);

        // 초기화 성공 응답, {"message": "reset ok"} 형태로 JSON이 내려감
        return ResponseEntity.ok(Map.of("message", "reset ok"));
    }

    // 프론트 모달창에서 좌석을 다 골랐을 때 상태를 처리해주려고 뚫어둔 엔드포인트
    // (현재는 추가 로직 확장을 위해 틀만 잡아둔 상태임)
    @PostMapping("/complete-seat")
    public ResponseEntity<?> completeSeat(Principal principal) {

        // 위 API들이랑 동일하게 로그인 여부 먼저 확인
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요한 서비스입니다."));
        }

        // 나중에 좌석 확정 로직 붙일 때 어느 사용자인지 구분하려고 미리 아이디 꺼내둔 것
        String userId = principal.getName();

        // 현재는 테스트용 응답만 반환하는 상태, 이 자리에 실제 로직이 들어올 예정
        return ResponseEntity.ok(Map.of("message", "test"));
    }
}
