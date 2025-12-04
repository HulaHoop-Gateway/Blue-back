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

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private Key secretKey;

    @PostConstruct
    public void init() {
        // ✅ Base64 인코딩 제거 (로그인/검증 둘 다 동일하게 사용)
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ✅ JWT 생성
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(username) // id 값 저장
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    // ✅ JWT 검증 (향상된 에러 처리)
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            System.err.println("❌ JWT 토큰이 만료되었습니다: " + e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            System.err.println("❌ 지원되지 않는 JWT 토큰입니다: " + e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            System.err.println("❌ 잘못된 형식의 JWT 토큰입니다: " + e.getMessage());
            return false;
        } catch (SignatureException e) {
            System.err.println("❌ JWT 서명이 유효하지 않습니다: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            System.err.println("❌ JWT 토큰이 비어있습니다: " + e.getMessage());
            return false;
        } catch (JwtException e) {
            System.err.println("❌ JWT 검증 실패: " + e.getMessage());
            return false;
        }
    }

    // ✅ JWT에서 사용자 아이디 추출
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // ✅ JWT 만료 여부 확인
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
            return true; // 토큰 파싱 실패 시 만료된 것으로 간주
        }
    }

    // ✅ JWT 만료 시간 가져오기
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

    // ✅ 토큰 검증 실패 원인 반환
    public String getValidationError(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return null; // 유효한 토큰
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
