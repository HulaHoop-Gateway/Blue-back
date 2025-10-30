package com.hulahoop.blueback.member.model.service;

import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    // ✅ 아이디 중복 확인
    public boolean isIdAvailable(String id) {
        return userMapper.countById(id) == 0;
    }

    // ✅ 회원가입 처리
    public void register(MemberDTO member) {
        if (!isIdAvailable(member.getId())) {
            throw new RuntimeException("이미 사용 중인 아이디입니다.");
        }

        String lastCode = userMapper.findLastMemberCode();
        String newCode = generateNextCode(lastCode);
        member.setMemberCode(newCode);

        member.setPassword(passwordEncoder.encode(member.getPassword()));
        member.setUserType("U");

        // ✅ React에서 보내지 않았다면 기본값
        if (member.getNotificationStatus() == null) {
            member.setNotificationStatus("Y");
        }

        int result = userMapper.insertMember(member);
        if (result == 0) {
            throw new RuntimeException("회원가입 실패");
        }
    }

    private String generateNextCode(String lastCode) {
        if (lastCode == null) return "U000000001";
        int num = Integer.parseInt(lastCode.substring(1)) + 1;
        return String.format("U%09d", num);
    }
}
