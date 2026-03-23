package com.hulahoop.blueback.member.model.service;

import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;

@Service
public class MemberService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JavaMailSender mailSender;

    public MemberService(UserMapper userMapper, JavaMailSender mailSender) {
        this.userMapper = userMapper;
        this.mailSender = mailSender;
    }

    // ===== 회원가입 관련 =====

    // 각 항목이 DB에 이미 있는지 확인 - count가 0이면 사용 가능
    public boolean isIdAvailable(String id) {
        return userMapper.countById(id) == 0;
    }

    public boolean isEmailAvailable(String email) {
        return userMapper.countByEmail(email) == 0;
    }

    public boolean isPhoneNumAvailable(String phoneNum) {
        return userMapper.countByPhoneNum(phoneNum) == 0;
    }

    public void register(MemberDTO member) {
        // 중복 확인 먼저 - 실패하면 RuntimeException을 던져서 컨트롤러에서 잡음
        if (!isIdAvailable(member.getId())) {
            throw new RuntimeException("이미 사용 중인 아이디입니다.");
        }
        if (!isEmailAvailable(member.getEmail())) {
            throw new RuntimeException("이미 사용 중인 이메일입니다.");
        }
        if (!isPhoneNumAvailable(member.getPhoneNum())) {
            throw new RuntimeException("이미 사용 중인 전화번호입니다.");
        }

        // 마지막 코드를 조회해서 다음 코드 생성 (U000000001 형식)
        String lastCode = userMapper.findLastMemberCode();
        String newCode = generateNextCode(lastCode);
        member.setMemberCode(newCode);

        // 비밀번호는 반드시 암호화해서 저장
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

    // 마지막 코드에서 숫자 부분만 파싱해서 1 더한 후 다시 포맷
    private String generateNextCode(String lastCode) {
        if (lastCode == null)
            return "U000000001";
        int num = Integer.parseInt(lastCode.substring(1)) + 1;
        return String.format("U%09d", num);
    }

    // ===== 마이페이지 / 회원정보 관리 =====

    // id로 회원 조회 - 탈퇴한 회원도 DB에는 남아있기 때문에 member_yn 확인 필요
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

    public void updateMember(MemberDTO dto) {
        int result = userMapper.updateMember(dto);
        if (result == 0) {
            throw new RuntimeException("회원정보 수정 실패");
        }
    }

    public void updateNotification(String memberCode, boolean enabled) {
        int result = userMapper.updateNotification(memberCode, enabled ? "Y" : "N");
        if (result == 0) {
            throw new RuntimeException("SNS 알림 설정 변경 실패");
        }
    }

    // 소프트 딜리트 방식 - 실제로 행을 지우지 않고 member_yn을 'N'으로 변경
    public void withdrawMember(String memberCode) {
        int result = userMapper.withdrawMember(memberCode);
        if (result == 0) {
            throw new RuntimeException("회원 탈퇴 실패");
        }
    }

    public String findIdByNameAndEmail(String name, String email) {
        MemberDTO member = userMapper.findByNameAndEmail(name, email);
        if (member == null)
            throw new RuntimeException("일치하는 회원이 없습니다.");
        return member.getId();
    }

    // 임시 비밀번호 발급 - 생성 후 암호화해서 DB 업데이트, 원문은 이메일로 발송
    public void sendTempPassword(String id, String email) {
        MemberDTO member = userMapper.findByIdAndEmail(id, email);
        if (member == null)
            throw new RuntimeException("입력 정보와 일치하는 회원이 없습니다.");

        String tempPwd = generateTempPassword();
        String encodedPwd = passwordEncoder.encode(tempPwd);

        // 비밀번호 컬럼만 업데이트 (전체 수정 쿼리 쓰면 다른 컬럼까지 영향 받을 수 있어서 분리)
        userMapper.updatePassword(member.getMemberCode(), encodedPwd);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[훌라후프] 임시 비밀번호 발급 안내");
        message.setText("임시 비밀번호: " + tempPwd + "\n로그인 후 반드시 내 정보에서 비밀번호를 변경해주세요.");
        mailSender.send(message);
    }

    // 비밀번호 변경 - 현재 비밀번호 먼저 검증, 통과하면 새 비밀번호로 교체
    public void changePassword(String id, String currentPwd, String newPwd) {
        MemberDTO member = userMapper.findById(id);
        if (member == null)
            throw new RuntimeException("회원 정보를 찾을 수 없습니다.");

        if (!passwordEncoder.matches(currentPwd, member.getPassword())) {
            throw new RuntimeException("현재 비밀번호가 일치하지 않습니다.");
        }

        String encodedNewPwd = passwordEncoder.encode(newPwd);
        userMapper.updatePassword(member.getMemberCode(), encodedNewPwd);
    }

    private String generateTempPassword() {
        return Long.toString((long) (Math.random() * 10000000000L)); // 10자리 랜덤 숫자
    }
}
