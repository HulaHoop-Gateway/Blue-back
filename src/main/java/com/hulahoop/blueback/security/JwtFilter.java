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
            "/api/payments/confirm",
            "/api/ai/reset"
    );


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

        // 1) OPTIONS 요청은 항상 통과 (CORS)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) 공개 경로는 JWT 검증 제외
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3) JWT Authorization 헤더 검증
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, null);

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } else {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Invalid Token");
                return;
            }

        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Missing or Invalid Authorization Header");
            return;
        }

        // 4) 다음 필터로 진행
        filterChain.doFilter(request, response);
    }

    // 공개 허용 경로 체크
    private boolean isPublicPath(String path) {
        if (path == null) return false;

        // 마지막 슬래시 제거
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
