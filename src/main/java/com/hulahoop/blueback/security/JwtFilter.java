package com.hulahoop.blueback.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// OncePerRequestFilter를 상속하면 요청당 딱 한 번만 필터가 실행됨
// 스프링 시큐리티 필터 체인에 끼워놓아서 컨트롤러 도달 전에 JWT를 먼저 검사함
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // 로그인, 회원가입처럼 토큰 없이도 접근 가능해야 하는 경로 목록
    // 여기 포함된 경로는 JWT 검증을 건너뜀
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/login",
            "/api/member/signup",
            "/api/member/check-id",
            "/api/member/check-email",
            "/api/member/check-phone",
            "/api/member/find-id",
            "/api/member/reset-password",
            "/api/ai/reset",
            "/api/payments",
            "/api/payments/create",
            "/api/payments/confirm");

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        System.out.println("[JwtFilter] 요청 경로: " + path + " | 메소드: " + method);

        // OPTIONS 요청은 브라우저가 실제 요청 전에 CORS 사전 확인을 위해 보내는 거라
        // JWT 검증 없이 통과시켜야 함 (막으면 CORS 에러 남)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 공개 경로는 토큰 없어도 되니까 검증 없이 바로 다음 필터로 넘김
        if (isPublicPath(path)) {
            System.out.println("[JwtFilter] 공개 경로 - JWT 검증 건너뜀: " + path);
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 헤더에서 토큰을 꺼냄
        // 형식이 "Bearer 토큰값" 이어야 함 - 이게 HTTP 표준 인증 헤더 형식
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.err.println("[JwtFilter] Authorization 헤더 없음 또는 형식 오류: " + path);
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "MISSING_TOKEN",
                    "로그인이 필요합니다. Authorization 헤더가 없거나 형식이 잘못되었습니다.");
            return;
        }

        // "Bearer " 뒤의 실제 토큰 값만 잘라냄 (앞 7글자 제거)
        String token = authHeader.substring(7);
        System.out.println("[JwtFilter] JWT 토큰 검증 시작...");

        // 토큰 유효성 검사 - 만료, 위조, 빈 값 등 여러 경우 체크
        if (!jwtUtil.validateToken(token)) {
            String errorType = jwtUtil.getValidationError(token);
            System.err.println("[JwtFilter] JWT 검증 실패 - 원인: " + errorType);

            // 실패 원인에 따라 다른 메시지를 내려줌 (프론트에서 에러 분기 처리할 수 있도록)
            String errorMessage = switch (errorType) {
                case "TOKEN_EXPIRED" -> "토큰이 만료되었습니다. 다시 로그인해주세요.";
                case "TOKEN_MALFORMED" -> "토큰 형식이 올바르지 않습니다.";
                case "TOKEN_INVALID_SIGNATURE" -> "토큰 서명이 유효하지 않습니다.";
                case "TOKEN_EMPTY" -> "토큰이 비어있습니다.";
                default -> "유효하지 않은 토큰입니다.";
            };

            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, errorType, errorMessage);
            return;
        }

        // 토큰이 유효하면 안에 담긴 사용자 아이디를 꺼내서 인증 객체를 만들어줌
        // SecurityContextHolder에 넣어두면 이후 컨트롤러에서 인증 정보를 꺼내 쓸 수 있음
        String username = jwtUtil.extractUsername(token);
        System.out.println("[JwtFilter] JWT 검증 성공 - 사용자: " + username);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username,
                null, null);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 검증 완료 - 다음 필터 또는 컨트롤러로 요청 넘김
        filterChain.doFilter(request, response);
    }

    // 요청 경로가 공개 허용 목록에 포함되는지 확인
    // startsWith를 써서 /api/payments/confirm 같은 하위 경로도 함께 처리됨
    private boolean isPublicPath(String path) {
        if (path == null)
            return false;

        // 끝에 슬래시가 붙어 있으면 제거해서 비교 일관성 유지
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    // 에러 응답을 JSON 형식으로 직접 써서 내려보냄
    // 필터 단계에서 예외를 던지면 스프링 예외 처리가 안 타기 때문에 직접 response에 씀
    private void sendJsonError(HttpServletResponse response, int status, String errorType, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jsonResponse = String.format(
                "{\"error\": \"%s\", \"message\": \"%s\", \"status\": %d}",
                errorType, message, status);

        response.getWriter().write(jsonResponse);
    }
}
