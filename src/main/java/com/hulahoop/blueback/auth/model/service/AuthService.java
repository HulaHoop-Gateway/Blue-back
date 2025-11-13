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
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    // ✅ 로그인 후 JWT 발급
    public String login(String id, String rawPassword) {
        log.info("🔐 로그인 시도: {}", id);

        MemberDTO member = userMapper.findById(id);
        log.info("🔍 조회된 회원 정보: {}", member);

        if (member == null) {
            log.warn("❌ 존재하지 않는 아이디: {}", id);
            throw new RuntimeException("존재하지 않는 아이디입니다.");
        }

        boolean passwordMatch = passwordEncoder.matches(rawPassword, member.getPassword());
        log.info("🔑 비밀번호 일치 여부: {}", passwordMatch);

        if (!passwordMatch) {
            log.warn("❌ 비밀번호 불일치: 입력={}, 저장={}", rawPassword, member.getPassword());
            throw new RuntimeException("비밀번호가 올바르지 않습니다.");
        }

        String token = jwtUtil.generateToken(member.getId());
        log.info("🎫 발급된 JWT: {}", token);

        return token;
    }
}
