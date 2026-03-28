package com.hulahoop.blueback.auth.controller;

import com.hulahoop.blueback.auth.model.dto.LoginRequest;
import com.hulahoop.blueback.auth.model.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 사용자가 아이디/비밀번호를 치고 들어올 때 가장 먼저 맞이하는 컨트롤러
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 로그인 엔드포인트
    // 프론트엔드에서 JSON 바디로 온 아이디와 비밀번호를 LoginRequest DTO로 쏙 매핑받아서 가져옴
    // 성공하면 AuthService가 만들어준 예쁜 토큰 문자열을 반환하고,
    // 실패하면 AuthService 안에서 RuntimeException이 터져서 글로벌 에러 핸들러나 500/400에러로 빠지게 됨
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {

        // 서비스 계층으로 실제 검증 로직 위임 (아이디, 비번 통과하면 토큰이 툭 튀어나옴)
        String token = authService.login(loginRequest.getId(), loginRequest.getPassword());

        // 발급받은 토큰을 JSON 객체 {"token": "ey..."} 형태로 감싸서 응답 코드로 200(OK)과 함께 리턴함
        // 프론트엔드는 이 응답을 받고 브라우저의 로컬스토리지나 세션스토리지에 토큰을 저장하게 됨
        return ResponseEntity.ok(Map.of("token", token));
    }
}
