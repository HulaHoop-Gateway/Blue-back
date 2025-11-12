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

        // ✅ 1. CORS Preflight(OPTIONS) 요청은 바로 통과
        if ("OPTIONS".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 2. 로그인 / 회원가입 / 아이디중복확인은 토큰 검증 제외
        if (path.equals("/api/login")
                || path.equals("/api/member/signup")
                || path.equals("/api/member/check-id")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 3. Authorization 헤더 검증
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);
                System.out.println("[JwtFilter] 유효한 토큰 - 사용자: " + username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, null);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                System.out.println("[JwtFilter] ❌ 토큰이 유효하지 않음");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Invalid Token");
                return;
            }
        } else {
            System.out.println("[JwtFilter] ❌ Authorization 헤더 없음");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Missing or Invalid Authorization Header");
            return;
        }

        // ✅ 4. 체인 계속 진행
        filterChain.doFilter(request, response);
    }
}
