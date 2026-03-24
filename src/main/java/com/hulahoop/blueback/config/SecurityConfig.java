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

// 스프링 시큐리티 전체 규칙을 설정하는 메인 기지 같은 클래스
@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    // 우리가 만든 커스텀 JwtFilter를 생성자로 주입받아옴
    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    // 서버의 보안 룰을 세팅하는 메인 메서드
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // CORS 설정: 서버가 다른 도메인(프론트엔드)에서 오는 요청을 거부하지 않고 받을지 결정함.
                // 아래 만들어둔 corsConfigurationSource 메서드의 설정을 따라가게 함
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF(사이트 간 요청 위조) 공격 방어 설정.
                // 보통 쿠키/세션 기반일 때 필요한데 우린 모바일친화적이고 stateless한 JWT를 쓰니까 꺼버림(disable)
                .csrf(csrf -> csrf.disable())

                // 중요한 설정! 스프링아, 내 서버는 클라이언트 상태를 기억하는 '세션'을 쓰지 않을 거니까(STATELESS)
                // 메모리 낭비하면서 무겁게 세션 굽지 마라 라고 지시하는 거임
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 각 API 경로별로 인증을 얼마나 빡세게(?) 할지 나누는 작업
                .authorizeHttpRequests(auth -> auth
                        // 브라우저가 본 요청(POST, PUT 등) 보내기 전에 "여기 요청 보내도 안전해?" 하고
                        // OPTIONS 메서드로 찔러보는 건 아묻따 그냥 통과하게 비워줌 (이거 안 열면 CORS 에러 폭탄 터짐)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 결제 웹훅이나 결제 생성/승인 등 서드파티 모듈이 찔러도 응답해야하는 곳은 모두 접근 오케이
                        .requestMatchers("/api/payments/**").permitAll()

                        // 회원이 아니거나 로그인을 안 한 사람도 당연히 접근해야 하는 필수 경로들 완전 개방세팅
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

                        // 위에 적어둔 녀석들을 제외한 나머지 '모든' API 호출(.anyRequest())은
                        // 무조건 정상적인 JWT 토큰을 까서 통과한 권한자(.authenticated())만 접근할 수 있게 꽉 잠금
                        .anyRequest().authenticated())

                // 옛날 스프링 시큐리티가 지원하던 기본 폼 로그인 창 (아이디/비번 치는 하얀 창) 로직을 아예 꺼버림
                // 우리는 JSON 통신으로 직접 로그인을 태우고 토큰으로만 소통할 거니까 쓸모없음
                .formLogin(form -> form.disable())

                // 이 부분이 핵심!
                // 스프링이 원래 기본으로 돌리는 `UsernamePasswordAuthenticationFilter`(아이디비번 파싱해서 검증하는 애)가
                // 돌아가기 **바로 직전**에,
                // 우리가 방금 만든 커스텀 `jwtFilter`를 먼저 끼워넣기하겠다는 설정임.
                // 그래서 클라이언트 요청 -> 우리가 짠 jwtFilter 먼저 구동 -> 인증성공하면 뒤에 놈들은 패스됨.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build(); // 설정 끝 객체 리턴!
    }

    // 인증 과정 묶음 전체를 관리하는 AuthenticationManager. 다른 로직에서 주입받아 쓰기 위해 만들어 둠 (여기선
    // AuthService가 로그인 검증할때 씀)
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // 회원가입할 때 평문 비밀번호를 막 저장하면 클나니까, 돌이킬 수 없는 해시값으로 꼬아주는 인코더 빈
    // BCrypt가 비밀번호 안전성 쪽으로는 국룰이기 때문에 이거 하나 쓰면 됨
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 처음에 등록한 CORS 세부 설정을 잡아주는 곳. 프론트엔드가 누군지 지정해주는 블랙리스트/화이트리스트임
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 프론트엔드 작업 포트로 로컬에 켜진 5173(Vite용), 3000(CRA용)을 모두 방문 허용 해줌
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        // 우리 API 서버가 어떤 메서드를 처리할 수 있는지 적어줌
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 아무 헤더나 다 받아주도록 열어둠 (`*` 사용)
        config.setAllowedHeaders(List.of("*"));

        // 프론트의 Axios에서 withCredentials: true 세팅을 날릴 때 서버가 쿠키/인증 헤더 값을 정상적으로 받게 하려면
        // 이 속성이 반드시 활성화(true)되어야 함. 단, 이거 true일 때는 setAllowedOrigins("*") (전체오픈)이 안 되기
        // 때문에 위에 명시해줌
        config.setAllowCredentials(true);

        // 기본적으로 클라이언트에서 Authorization 헤더 값을 읽으려 하면 브라우저 보안에서 숨겨버림
        // 이걸 Exposed로 열어줘야 프론트에서 res.headers.authorization 등으로 뽑아서 저장할 수 있게 됨
        config.setExposedHeaders(List.of("Authorization"));

        // 위에서 고생해서 짠 규칙을 '모든 경로(/**)'에 다 매핑해라!
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
