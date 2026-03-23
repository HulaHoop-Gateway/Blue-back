package com.hulahoop.blueback.auth.model.service;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        // BCryptPasswordEncoder는 비밀번호를 암호화/비교할 때 쓰는 스프링 시큐리티 제공 클래스
        // 생성자에서 직접 new로 만들어도 되고, 빈으로 등록해서 주입받아도 됨
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    // 로그인 요청이 들어오면 DB에서 회원 조회 후 비밀번호 비교, 맞으면 JWT 토큰 발급
    public String login(String id, String rawPassword) {
        log.info("로그인 시도: {}", id);

        MemberDTO member = userMapper.findById(id);
        log.info("조회된 회원 정보: {}", member);

        // 아이디가 DB에 없으면 null 반환됨 - 이 경우 바로 예외 던지기
        if (member == null) {
            log.warn("존재하지 않는 아이디: {}", id);
            throw new RuntimeException("존재하지 않는 아이디입니다.");
        }

        // BCrypt는 같은 비밀번호도 매번 다르게 암호화하기 때문에 matches()로만 비교 가능
        // rawPassword는 사용자가 입력한 원본, member.getPassword()는 DB에 저장된 암호화된 값
        boolean passwordMatch = passwordEncoder.matches(rawPassword, member.getPassword());
        log.info("비밀번호 일치 여부: {}", passwordMatch);

        if (!passwordMatch) {
            log.warn("비밀번호 불일치: 입력={}, 저장={}", rawPassword, member.getPassword());
            throw new RuntimeException("비밀번호가 올바르지 않습니다.");
        }

        // 검증 통과 시 JWT 토큰 생성 - 이 토큰을 클라이언트가 이후 요청에 담아서 보냄
        String token = jwtUtil.generateToken(member.getId());
        log.info("발급된 JWT: {}", token);

        return token;
    }
}
