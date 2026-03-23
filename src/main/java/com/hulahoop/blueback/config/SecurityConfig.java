package com.hulahoop.blueback.config;

import com.hulahoop.blueback.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // JWT 방식이라 세션 안 씀 - STATELESS로 설정해야 서버가 세션을 만들지 않음
                // CSRF도 쿠키 기반이 아니라서 비활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 브라우저가 실제 요청 보내기 전 OPTIONS로 CORS 사전 확인을 하기 때문에 열어둬야 함
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 결제 API는 비로그인도 접근 가능하도록 허용
                        .requestMatchers("/api/payments/**").permitAll()

                        // 로그인, 회원가입, 아이디/비번 찾기 - 토큰 없이 접근해야 하는 경로들
                        .requestMatchers(
                                "/api/login",
                                "/api/member/signup",
                                "/api/member/check-id",
                                "/api/member/check-email",
                                "/api/member/check-phone",
                                "/api/member/find-id",
                                "/api/member/reset-password",
                                "/api/ai/reset")
                        .permitAll()

                        // 그 외는 전부 JWT 인증 필요
                        .anyRequest().authenticated())

                // 스프링 시큐리티 기본 폼 로그인은 쓰지 않음
                .formLogin(form -> form.disable())

                // UsernamePasswordAuthenticationFilter 앞에 내가 만든 JwtFilter를 끼워 넣음
                // 이렇게 해야 컨트롤러 도달 전에 토큰 검증이 먼저 실행됨
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 로컬 개발 환경에서 프론트가 5173(Vite), 3000(CRA) 포트를 주로 씀
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        // axios에서 withCredentials: true 옵션 쓸 때 이게 true여야 쿠키/인증 헤더가 전달됨
        config.setAllowCredentials(true);

        // Authorization 헤더를 클라이언트에서 읽을 수 있도록 노출
        config.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
