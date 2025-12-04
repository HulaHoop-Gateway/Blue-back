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

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // 공개 허용 경로 목록
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/login",
            "/api/member/signup",
            "/api/member/check-id",
            "/api/member/check-email",
            "/api/member/check-phone",
            "/api/member/find-id",
            "/api/member/reset-password",
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

        System.out.println("🔍 [JwtFilter] 요청 경로: " + path + " | 메소드: " + method);

        // 1) OPTIONS 요청은 항상 통과 (CORS)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) 공개 경로는 JWT 검증 제외
        if (isPublicPath(path)) {
            System.out.println("✅ [JwtFilter] 공개 경로 - JWT 검증 건너뜀: " + path);
            filterChain.doFilter(request, response);
            return;
        }

        // 3) JWT Authorization 헤더 검증
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.err.println("❌ [JwtFilter] Authorization 헤더 없음 또는 잘못된 형식: " + path);
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "MISSING_TOKEN",
                    "로그인이 필요합니다. Authorization 헤더가 없거나 형식이 잘못되었습니다.");
            return;
        }

        String token = authHeader.substring(7);
        System.out.println("🔑 [JwtFilter] JWT 토큰 검증 시작...");

        // 토큰 검증
        if (!jwtUtil.validateToken(token)) {
            String errorType = jwtUtil.getValidationError(token);
            System.err.println("❌ [JwtFilter] JWT 검증 실패 - 원인: " + errorType);

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

        // 토큰에서 사용자 정보 추출
        String username = jwtUtil.extractUsername(token);
        System.out.println("✅ [JwtFilter] JWT 검증 성공 - 사용자: " + username);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username,
                null, null);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 4) 다음 필터로 진행
        filterChain.doFilter(request, response);
    }

    // 공개 허용 경로 체크
    private boolean isPublicPath(String path) {
        if (path == null)
            return false;

        // 마지막 슬래시 제거
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    // JSON 형식 에러 응답
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
