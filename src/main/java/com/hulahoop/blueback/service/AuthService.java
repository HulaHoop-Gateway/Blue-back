package com.hulahoop.blueback.service;

import com.hulahoop.blueback.dto.MemberDTO;
import com.hulahoop.blueback.dao.UserMapper;
import com.hulahoop.blueback.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper; // ✅ MyBatis 매퍼 사용
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    // ✅ 로그인 후 JWT 발급
    public String login(String username, String rawPassword) {
        MemberDTO member = userMapper.findByUsername(username);

        if (member == null) {
            throw new RuntimeException("아이디 없음");
        }

        // DB에 저장된 BCrypt 해시와 비교
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new RuntimeException("비밀번호 틀림");
        }

        // JWT 생성 후 반환
        return jwtUtil.generateToken(member.getUsername());
    }

    // ✅ 회원가입 기능 (선택사항)
    public void register(MemberDTO member) {
        // 비밀번호 암호화 후 저장
        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPassword);

        int result = userMapper.insertMember(member);
        if (result == 0) {
            throw new RuntimeException("회원가입 실패");
        }
    }
}
