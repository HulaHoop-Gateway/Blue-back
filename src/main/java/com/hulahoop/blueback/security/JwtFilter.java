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

// JWT 필터 핵심: 클라이언트가 우리 서버로 요청을 보낼 때, 컨트롤러에 도착하기 전 맨 앞에서 먼저 가로채서 검사하는 녀석임
// [프론트와의 연결고리]
// Blue-front의 axiosInstance.js (src/api/axiosInstance.js) Request Interceptor에서
// 모든 API 요청 직전에 sessionStorage의 'user_jwt' 토큰을 꺼내 HTTP 헤더에 자동으로 꽂아서 보냄.
//
// @Component: "스프링아, 이 커스텀 필터 클래스도 네가 관리하는 부품(Bean) 중 하나로 등록해서 다른 곳에서 쓸 수 있게 해체다오~" 하고 선언하는 마크임.
@Component
// 상속(extends): 스프링 코어가 이미 잘 만들어둔 OncePerRequestFilter(단일 요청당 무조건 딱 1번만 실행됨을
// 보장하는 필터)의 모든 뼈대와 기능을 그대로 물려받아(상속) 내 맘대로 살을 얹음.
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // 로그인, 회원가입, 비밀번호 찾기처럼 '아직 로그인 전이라 토큰이 아예 없는 상황'에서
    // 사용자가 접근해도 막지 말아야 할 URL들을 묶어놓은 리스트
    // 나중에 코드 로직에서 이 리스트에 포함된 경로는 토큰 여부를 묻지도 따지지도 않고 바로 프리패스시킴
    //
    // [프론트와의 연결고리]
    // axiosInstance.js에서는 이 경로들에 대해서도 Request Interceptor가 실행되며 토큰을 헤더에 꽂으려 시도함.
    // 하지만 경로가 PUBLIC_PATHS에 포함되면 이 필터에서 검증 없이 통과시키므로, 토큰 유무가 결과에 영향 없음.
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

    // 여기가 필터의 메인 동작 흐름
    // @Override: 부모 필터(OncePerRequestFilter)가 가진 빈 껍데기 메서드를, 내가 짜놓은 나만의 토큰 검증 로직으로
    // 완전히 덮어써서 재정의(다형성 작동) 하겠다는 뜻임.
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // 지금 클라이언트가 어디로 무슨 방식(GET, POST 등)으로 오고 있는지 확인
        String path = request.getRequestURI();
        String method = request.getMethod();

        System.out.println("[JwtFilter] 요청 경로: " + path + " | 메소드: " + method);

        // 중요 포인트: 브라우저는 다른 도메인으로 진짜 데이터를 보내기 전에 안전한지 살피려고 OPTIONS 메소드로 찔러봄(Preflight
        // Request)
        // 이 OPTIONS 요청은 토큰 없이 빈몸으로 날아오기 때문에, 이 단계에서 토큰 검사를 해버리면 무조건 컷트당함 (CORS 오류 발생의
        // 주범임)
        // 그래서 OPTIONS 요청은 바로 통과시켜야 함
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 우리가 위에서 등록한 토큰 필요없는 공개 경로 목록인지 확인
        // 만약 맞다면 쓸데없이 헤더 까서 토큰 검증할 필요 없으니 바로 통과시킴
        if (isPublicPath(path)) {
            System.out.println("[JwtFilter] 공개 경로 - JWT 검증 건너뜀: " + path);
            filterChain.doFilter(request, response);
            return;
        }

        // --- 여기서부터는 필수로 토큰이 있어야 하는 경로들임 ---

        // HTTP 요청 헤더에서 'Authorization' 이라는 이름으로 된 값을 빼옴
        // JWT 방식은 기본적으로 "Bearer 나토큰이다어쩌고저쩌고" 형태로 보내는 게 전세계 공통 규칙임
        //
        // [프론트와의 연결고리]
        // axiosInstance.js Request Interceptor에서:
        // config.headers.Authorization = `Bearer ${token}`;
        // 이 코드가 이 헤더를 세팅함. 여기서 꺼내서 'Bearer ' 7글자 잘라낸 뒤 토큰만 검증.
        String authHeader = request.getHeader("Authorization");

        // 헤더 자체가 없거나, "Bearer "로 제대로 시작하지 않으면 문전박대
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.err.println("[JwtFilter] Authorization 헤더 없음 또는 형식 오류: " + path);
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "MISSING_TOKEN",
                    "로그인이 필요합니다. Authorization 헤더가 없거나 형식이 잘못되었습니다.");
            return;
        }

        // 인증 텍스트에서 불필요한 "Bearer " (공백 포함 7글자) 문자열을 잘라내고, 순수한 토큰 문자열만 확보함
        String token = authHeader.substring(7);
        System.out.println("[JwtFilter] JWT 토큰 검증 시작...");

        // JwtUtil의 헬퍼 메서드를 써서 이 토큰이 만료됐거나, 변조됐거나, 이상한 게 아닌지 검증
        // 값이 문제가 있으면 안쪽으로 진입함
        if (!jwtUtil.validateToken(token)) {
            // 어떤 이유로 실패했는지 원인을 문자열로 가져옴 ("TOKEN_EXPIRED" 등등)
            String errorType = jwtUtil.getValidationError(token);
            System.err.println("[JwtFilter] JWT 검증 실패 - 원인: " + errorType);

            // 실패 원인에 따라 프론트 개발자가 원인을 직관적으로 파악할 수 있도록 우리말로 에러 메시지 매핑
            String errorMessage = switch (errorType) {
                case "TOKEN_EXPIRED" -> "토큰이 만료되었습니다. 다시 로그인해주세요.";
                case "TOKEN_MALFORMED" -> "토큰 형식이 올바르지 않습니다.";
                case "TOKEN_INVALID_SIGNATURE" -> "토큰 서명이 유효하지 않습니다.";
                case "TOKEN_EMPTY" -> "토큰이 비어있습니다.";
                default -> "유효하지 않은 토큰입니다.";
            };

            // 필터에서 직접 401(Unauthorized) 찍고 메시지를 JSON으로 만들어서 브라우저에 던지고 종료
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, errorType, errorMessage);
            return;
        }

        // --- 여기까지 코드가 왔다는 건 = 토큰이 정상이라는 뜻 ---

        // 토큰 까서 이 사람이 누구인지 아이디(Username) 알아냄
        String username = jwtUtil.extractUsername(token);
        System.out.println("[JwtFilter] JWT 검증 성공 - 사용자: " + username);

        // 스프링 시큐리티한테 "어, 이 사람 인증된 회원이야. 아이디 남겨놓을게" 라고 알려줄 통행증 생성
        // 권한이나 비밀번호는 여기서 필요 없으니 null로 넣음 (토큰 자체가 이미 증명서니까)
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username,
                null, null);

        // 우리가 만든 통행증(Authentication 객체)을 SecurityContextHolder(스프링의 전역 보안 저장소)에 보관함
        // 이렇게 해두면 뒤에 있는 MemberController 같은 곳에서 `@AuthenticationPrincipal`만 써도 유저 정보를
        // 꺼내올 수 있음
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 무사히 모든 검문을 마쳤으니 원래 가려던 다음 필터나 최종 컨트롤러로 요청을 보내줌
        filterChain.doFilter(request, response);
    }

    // 들어온 URL 경로가 공개 허용 목록들 중 하나라도 포함되는지 검사하는 헬퍼 기능
    private boolean isPublicPath(String path) {
        if (path == null)
            return false;

        // /api/login/ 이렇게 뒤에 슬래시가 실수로 붙어올 경우 그냥 /api/login 이랑 똑같이 취급해주려고 공백 잘라냄
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // startsWith를 쓰는 이유: /api/payments 와 /api/payments/create 둘 다 똑같이 프리패스 시켜버리기
        // 위해서
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    // 필터 레벨에서는 예외 처리를 던지면 Spring의 @ExceptionHandler 같은 글로벌 에러 처리가 커버해주지 못함
    // 그래서 HttpServletResponse를 잡고 수동으로 JSON 포맷 응답 객체를 만들어버리는 로직임
    private void sendJsonError(HttpServletResponse response, int status, String errorType, String message)
            throws IOException {
        response.setStatus(status); // 401이나 403 같은 응답 코드 지정
        response.setContentType("application/json"); // 나는 json을 반환할 거다 선언
        response.setCharacterEncoding("UTF-8"); // 한글 깨지지 않게 설정

        // json 문자열 조립
        String jsonResponse = String.format(
                "{\"error\": \"%s\", \"message\": \"%s\", \"status\": %d}",
                errorType, message, status);

        // response.getWriter()로 스트림을 열어서 직접 바이트/문자열을 쏴줌
        response.getWriter().write(jsonResponse);
    }
}
