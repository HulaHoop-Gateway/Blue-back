package com.hulahoop.blueback.auth.model.service;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 실제 로그인이 맞는지 틀린지 판별하고, 토큰까지 빚어내는 핵심 비즈니스 로직 클래스
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        // 회원이 입력한 비밀번호랑 DB에 암호화되어 저장된 비밀번호를 비교하기 위해 BCrypt 툴 사용
        // 보통 SecurityConfig에 @Bean으로 등록해놓고 가져다 써도 되지만, 여기선 독립적으로 객체 하나 파서 사용함
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtUtil = jwtUtil;
    }

    // 아이디랑 패스워드 검증 후 토큰 발급
    public String login(String id, String rawPassword) {
        log.info("로그인 시도: {}", id);

        // 1단계: DB에서 일단 이 아이디를 가진 사람이 있는지 뒤져봄
        MemberDTO member = userMapper.findById(id);
        log.info("조회된 회원 정보: {}", member);

        // 아이디 자체가 아예 DB에 등록이 안 된 유령회원이면 여기서 바로 컷 (예외 던짐)
        if (member == null) {
            log.warn("존재하지 않는 아이디: {}", id);
            throw new RuntimeException("존재하지 않는 아이디입니다.");
        }

        // 2단계: 아이디는 맞는데 비밀번호가 맞는지 체크
        // BCrypt는 솔트(무작위 문자열)를 치기 때문에 같은 "1234"라도 해시값이 매번 달라서 직접 문자열 비교(==)가 불가능함
        // 그래서 반드시 passwordEncoder.matches(내가 입력한 비번, DB에 저장된 해시값) 메서드한테 비교를 맡겨야 함
        boolean passwordMatch = passwordEncoder.matches(rawPassword, member.getPassword());
        log.info("비밀번호 일치 여부: {}", passwordMatch);

        // 틀렸으면 가차없이 예외 던져서 로그인 로직 중단
        if (!passwordMatch) {
            log.warn("비밀번호 불일치: 입력={}, 저장={}", rawPassword, member.getPassword());
            throw new RuntimeException("비밀번호가 올바르지 않습니다.");
        }

        // 3단계: 여기까지 코드가 살아남았다는 건 정상 회원이라는 뜻!
        // JwtUtil 한테 이 회원 아이디를 주고 서명된 마패(JWT)를 만들어달라고 부탁함
        String token = jwtUtil.generateToken(member.getId());
        log.info("발급된 JWT: {}", token);

        // 생성된 JWT 문자열을 컨트롤러로 넘겨줌
        return token;
    }
}
