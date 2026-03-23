package com.hulahoop.blueback.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // application.yml에서 주입받는 값들
    // jwt.secret은 토큰 서명에 쓰이는 비밀키, jwt.expiration-ms는 만료 시간(ms 단위)
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private Key secretKey;

    // 빈 초기화 후 한 번 실행됨 - 문자열 시크릿을 실제 서명용 Key 객체로 변환
    // Base64 인코딩 없이 바이트 배열로 바로 변환하는 방식 사용
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    // 사용자 아이디를 Subject에 담아 JWT를 생성함
    // 발급 시간과 만료 시간도 함께 설정해서 나중에 검증할 때 쓸 수 있음
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(username) // 토큰 안에 사용자 아이디 저장
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    // 토큰이 유효한지 파싱해서 확인 - 예외 종류에 따라 각각 잡아줌
    // 유효하면 true, 어떤 이유로든 유효하지 않으면 false
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            System.err.println("JWT 토큰 만료: " + e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            System.err.println("지원하지 않는 JWT 형식: " + e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            System.err.println("잘못된 JWT 형식: " + e.getMessage());
            return false;
        } catch (SignatureException e) {
            System.err.println("JWT 서명 불일치: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            System.err.println("JWT가 비어있음: " + e.getMessage());
            return false;
        } catch (JwtException e) {
            System.err.println("JWT 검증 실패: " + e.getMessage());
            return false;
        }
    }

    // 유효한 토큰에서 Subject(사용자 아이디)를 꺼냄
    // validateToken으로 먼저 검증하고 나서 호출해야 안전함
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // 토큰 만료 여부만 단독으로 확인할 때 쓰는 메서드
    // 파싱 자체가 실패하면 만료된 걸로 처리
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            return expiration.before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    // 만료 시간 자체를 Date 객체로 반환 - 필요한 경우에만 사용
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

    // 검증 실패 이유를 문자열로 반환 - JwtFilter에서 어떤 에러인지 구분할 때 씀
    // 유효하면 null 반환
    public String getValidationError(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return null;
        } catch (ExpiredJwtException e) {
            return "TOKEN_EXPIRED";
        } catch (UnsupportedJwtException e) {
            return "TOKEN_UNSUPPORTED";
        } catch (MalformedJwtException e) {
            return "TOKEN_MALFORMED";
        } catch (SignatureException e) {
            return "TOKEN_INVALID_SIGNATURE";
        } catch (IllegalArgumentException e) {
            return "TOKEN_EMPTY";
        } catch (JwtException e) {
            return "TOKEN_INVALID";
        }
    }
}
