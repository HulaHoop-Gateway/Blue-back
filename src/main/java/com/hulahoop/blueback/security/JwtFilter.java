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

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

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

        // ✅ 1️⃣ 로그로 요청 경로 확인
        System.out.println("\n[JwtFilter] 🚀 요청 경로: " + path + " (" + method + ")");

        // ✅ 2️⃣ Preflight (OPTIONS) 요청은 항상 허용
        if ("OPTIONS".equalsIgnoreCase(method)) {
            System.out.println("[JwtFilter] ✅ OPTIONS 요청 통과 (CORS preflight)");
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 3️⃣ 회원가입, 로그인 등 공개 경로는 JWT 검증 제외
        if (isPublicPath(path)) {
            System.out.println("[JwtFilter] ✅ 공개 경로로 인식되어 필터 통과: " + path);
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 4️⃣ JWT 인증 헤더 확인
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);
                System.out.println("[JwtFilter] 🔐 유효한 토큰 - 사용자: " + username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, null);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                System.out.println("[JwtFilter] ❌ 유효하지 않은 토큰");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Invalid Token");
                return;
            }
        } else {
            System.out.println("[JwtFilter] ❌ Authorization 헤더 없음 → 403");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Missing or Invalid Authorization Header");
            return;
        }

        // ✅ 5️⃣ 다음 필터로 진행
        filterChain.doFilter(request, response);
    }

    // ✅ 공개 경로 관리 (트레일링 슬래시와 쿼리스트링 대비)
    private boolean isPublicPath(String path) {
        if (path == null) return false;

        // 마지막 슬래시 제거
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // ✅ 공개 허용 API 목록
        return path.startsWith("/api/login")
                || path.startsWith("/api/member/signup")
                || path.startsWith("/api/member/check-id")
                || path.startsWith("/api/public")
                || path.startsWith("/api/test");
    }
}
