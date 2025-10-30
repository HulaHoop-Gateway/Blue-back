package com.hulahoop.blueback.auth.model.service;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

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
        MemberDTO member = userMapper.findById(id);

        if (member == null) {
            throw new RuntimeException("존재하지 않는 아이디입니다.");
        }

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new RuntimeException("비밀번호가 올바르지 않습니다.");
        }

        // ✅ JWT 생성 (id 기준)
        return jwtUtil.generateToken(member.getId());
    }
}
