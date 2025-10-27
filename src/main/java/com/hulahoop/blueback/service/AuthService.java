package com.hulahoop.blueback.service;

import com.hulahoop.blueback.model.Member;
import com.hulahoop.blueback.repository.UserRepository;
import com.hulahoop.blueback.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    // 로그인 후 JWT 발급
    public String login(String username, String rawPassword) {
        Optional<Member> optionalMember = userRepository.findByUsername(username);
        if (optionalMember.isEmpty()) {
            throw new RuntimeException("아이디 없음");
        }

        Member member = optionalMember.get();

        // DB에 있는 BCrypt 해시와 비교
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new RuntimeException("비밀번호 틀림");
        }

        // JWT 생성 후 반환
        return jwtUtil.generateToken(member.getUsername());
    }
}
