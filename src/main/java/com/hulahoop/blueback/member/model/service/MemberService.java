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

    // ======================================================
    // ✅ [회원가입 관련 기능]
    // ======================================================

    public boolean isIdAvailable(String id) {
        return userMapper.countById(id) == 0;
    }

    public void register(MemberDTO member) {
        if (!isIdAvailable(member.getId())) {
            throw new RuntimeException("이미 사용 중인 아이디입니다.");
        }

        String lastCode = userMapper.findLastMemberCode();
        String newCode = generateNextCode(lastCode);
        member.setMemberCode(newCode);
        member.setPassword(passwordEncoder.encode(member.getPassword()));
        member.setUserType("U");

        if (member.getNotificationStatus() == null) {
            member.setNotificationStatus("Y");
        }

        member.setMemberYn("Y");

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

    // ======================================================
    // ✅ [마이페이지 / 회원정보 관리 기능]
    // ======================================================

    // ✅ (id 기반) 회원 정보 조회
    public MemberDTO getMemberInfoById(String id) {
        MemberDTO dto = userMapper.findById(id);
        if (dto == null) {
            throw new RuntimeException("존재하지 않는 회원입니다.");
        }
        if (!"Y".equals(dto.getMemberYn())) {
            throw new RuntimeException("탈퇴된 회원입니다.");
        }
        return dto;
    }

    // ✅ 회원 정보 수정
    public void updateMember(MemberDTO dto) {
        int result = userMapper.updateMember(dto);
        if (result == 0) {
            throw new RuntimeException("회원정보 수정 실패");
        }
    }

    // ✅ SNS 알림 설정 변경
    public void updateNotification(String memberCode, boolean enabled) {
        int result = userMapper.updateNotification(memberCode, enabled ? "Y" : "N");
        if (result == 0) {
            throw new RuntimeException("SNS 알림 설정 변경 실패");
        }
    }

    // ✅ 회원 탈퇴 (member_yn = 'N' 으로 변경)
    public void withdrawMember(String memberCode) {
        int result = userMapper.withdrawMember(memberCode);
        if (result == 0) {
            throw new RuntimeException("회원 탈퇴 실패");
        }
    }
}
