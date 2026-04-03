package com.hulahoop.blueback.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;

// JwtUtil: 실질적으로 JWT 토큰을 만들고, 까서 확인하고, 만료시간 등을 관리하는 도우미 클래스
// 다른 서비스나 필터에서 이 클래스를 불러와서 토큰 관련 작업을 처리함
// @Component: 마찬가지로 이 유틸 클래스를 스프링 컨테이너에 싱글톤 부품(빈)으로 등록해서, 필터나 서비스 등 이곳저곳에서 의존성 주입(DI) 받아 맘껏 돌려 쓸 수 있게 만듦.
@Component
public class JwtUtil {

    // 토큰을 만들 때 사용하는 서명용 비밀키. 이 키가 유출되면 아무나 가짜 토큰을 만들 수 있으므로 절대 자바 코드에 대놓고 치지(하드코딩)
    // 않음.
    // @Value: application.yml 설정 파일에 보관된 중요한 환경변수 값(jwt.secret 등)을 쏙 뽑아서 이 자바 변수
    // 안에 동적으로 주입해주는 어노테이션임.
    @Value("${jwt.secret}")
    private String secret;

    // 만료 시간 설정. application.yml 파일에서 밀리초 단위로 가져옴 (예: 3600000 = 1시간)
    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // 위자료(secret)를 가공해서 만든 실제 암호화 키 객체
    private Key secretKey;

    // @PostConstruct: 스프링이 이 JwtUtil 빈 클래스를 처음 청사진대로 찍어내고 + 위에서 본 @Value 의존성 주입까지 싹
    // 다 끝마친 직후에,
    // 딱 1번만 자동으로 실행되게 예약 걸어두는 '초기화 전용 마크'임.
    // String 형태의 secret 값을 HMAC-SHA 알고리즘에 쓸 수 있는 진짜 Key 객체로 변환해서 저장해둠.
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    // 로그인에 성공했을 때 사용자에게 발급해줄 토큰을 생성하는 핵심 메서드
    // username(사용자 아이디 등)을 받아서 JWT의 Subject(발급 대상)에 넣어줌
    public String generateToken(String username) {
        Date now = new Date();
        // 발급 시간 + 유효 시간(밀리초) 더해서 언제 만료될지 Date 객체로 계산
        Date expiryDate = new Date(now.getTime() + expirationMs);

        // JWT 빌더 패턴을 사용해서 내용 구성
        return Jwts.builder()
                .setSubject(username) // 우리가 진짜 알고 싶은 데이터인 '누구의 토큰인가'를 적음
                .setIssuedAt(now) // 토큰 발급 일시 설정
                .setExpiration(expiryDate) // 위에서 계산한 토큰 만료 일시 설정
                .signWith(secretKey) // 우리 서버만 아는 시크릿 키로 서명 (도장 쾅)
                .compact(); // 최종적으로 String 형태의 긴 토큰 값으로 압축해서 반환
    }

    // 클라이언트가 보낸 토큰이 진짜 우리 서버가 발급한 게 맞는지, 만료되진 않았는지 검사하는 메서드
    // 문제가 있으면 Exception이 터지는데, try-catch로 잡아서 종류별로 처리
    public boolean validateToken(String token) {
        try {
            // 파서를 만들어서 키를 세팅하고, 토큰을 파싱(해석)해봄
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            // 에러 없이 통과하면 유효한 토큰!
            return true;
        } catch (ExpiredJwtException e) {
            // 예외 1: 토큰 구조도 맞고 서명도 맞지만, 지정된 유효기간이 지남
            System.err.println("JWT 토큰 만료: " + e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            // 예외 2: 헤더나 페이로드가 우리가 기대하는 형식이 아님
            System.err.println("지원하지 않는 JWT 형식: " + e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            // 예외 3: 토큰 문자열 자체가 망가져서 해석이 안 됨 (형태가 JWT가 아님)
            System.err.println("잘못된 JWT 형식: " + e.getMessage());
            return false;
        } catch (SignatureException e) {
            // 예외 4: 데이터는 읽히는데 서명이 다름 -> 누군가 데이터를 조작했거나 다른 서버 키로 만든 가짜 토큰
            System.err.println("JWT 서명 불일치: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            // 예외 5: 토큰 값이 아예 null이거나 비어있음
            System.err.println("JWT가 비어있음: " + e.getMessage());
            return false;
        } catch (JwtException e) {
            // 예외 6: 그 외 나머지 모든 JWT 관련 오류
            System.err.println("JWT 검증 실패: " + e.getMessage());
            return false;
        }
    }

    // 유효성이 검증된 토큰에서 핵심 정보인 '사용자 아이디(Subject)'를 꺼내는 메서드
    // 클레임(데이터 집합)을 열어서 Subject 필드만 읽어옴
    // 반드시 validateToken으로 진짜 토큰인지 확인한 후에 사용해야 보안상 안전함
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // 다른 정보를 파싱하지 않고 오직 '만료되었는가?'만 확인할 목적으로 만든 메서드
    public boolean isTokenExpired(String token) {
        try {
            // 클레임 바디 안에서 Expiration 필드만 꺼내서 Date 객체로 받음
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            // 만료 시각이 앞이면(현재 시각보다 전이면) 만료된 것임
            return expiration.before(new Date());
        } catch (JwtException e) {
            // 파싱하다 터지면 그냥 만료되거나 유효하지 않은 걸로 판단함
            return true;
        }
    }

    // 만료 시간 자체를 조회할 필요가 있을 때 호출하는 메서드
    public Date getExpirationDate(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
        } catch (JwtException e) {
            return null;
        }
    }

    // JwtFilter에서 클라이언트 쪽으로 명확한 실패 사유를 알려주기 위해, 그냥 true/false가 아니라 이유를 문자열로 리턴해줌
    // 문제가 없으면 null을 리턴 (null이면 정상 토큰이라는 뜻)
    public String getValidationError(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return null;
        } catch (ExpiredJwtException e) {
            return "TOKEN_EXPIRED"; // 프론트에서는 보통 이 에러를 받으면 재로그인이나 리프레시 토큰 로직을 태움
        } catch (UnsupportedJwtException e) {
            return "TOKEN_UNSUPPORTED";
        } catch (MalformedJwtException e) {
            return "TOKEN_MALFORMED";
        } catch (SignatureException e) {
            return "TOKEN_INVALID_SIGNATURE"; // 제일 위험한 케이스 (악의적 위조 의심)
        } catch (IllegalArgumentException e) {
            return "TOKEN_EMPTY";
        } catch (JwtException e) {
            return "TOKEN_INVALID";
        }
    }
}
