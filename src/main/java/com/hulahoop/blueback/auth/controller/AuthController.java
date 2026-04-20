package com.hulahoop.blueback.auth.controller;

import com.hulahoop.blueback.auth.model.dto.LoginRequest;
import com.hulahoop.blueback.auth.model.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 사용자가 아이디/비밀번호를 치고 들어올 때 가장 먼저 맞이하는 컨트롤러
// @RestController: 프론트엔드의 REST API 요청을 전담해서 받고, 화면(View)이 아닌 데이터(JSON) 자체를 바로 리턴해주는 API 전용 컨트롤러 마크임.
@RestController
// @RequestMapping: 이 클래스 안의 모든 엔드포인트 앞에 '/api'라는 주소를 공통으로 깔아줌.
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    // 생성자 의존성 주입(DI): 스프링 컨테이너가 켜질 때, 스스로 만든 AuthService 빈(Bean) 객체를 알아서 찾아 이 컨트롤러로
    // 쏙 꽂아줌.
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 로그인 엔드포인트
    // 성공하면 AuthService가 만들어준 예쁜 토큰 문자열을 반환하고,
    // 실패하면 AuthService 안에서 RuntimeException이 터져서 글로벌 에러 핸들러나 500/400에러로 빠지게 됨
    // @PostMapping: 보안이 중요한 로그인 데이터(계정/비번)는 주소창에 노출되지 않도록 HTTP 본문에 꽁꽁 숨겨서 전달하는 POST
    // 방식으로만 받아야 함.
    @PostMapping("/login")
    // @RequestBody: 프론트가 통째로 던진 JSON 덩어리를 스프링의 메시지 컨버터가 낚아채서, 내가 만든 자바
    // 객체(LoginRequest)로 자동 역직렬화 해줌.
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {

        // 서비스 계층으로 실제 검증 로직 위임 (아이디, 비번 통과하면 토큰이 툭 튀어나옴)
        String token = authService.login(loginRequest.getId(), loginRequest.getPassword());

        // 발급받은 토큰을 JSON 객체 {"token": "ey..."} 형태로 감싸서 응답 코드로 200(OK)과 함께 리턴함
        // 프론트엔드는 이 응답을 받고 브라우저의 로컬스토리지나 세션스토리지에 토큰을 저장하게 됨
        return ResponseEntity.ok(Map.of("token", token));
    }
}
