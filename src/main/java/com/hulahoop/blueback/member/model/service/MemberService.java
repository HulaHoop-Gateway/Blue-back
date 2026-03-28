package com.hulahoop.blueback.member.model.service;

import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;

// DB 트랜잭션, 데이터 조작 등 회원과 관련된 실질적인 "작업"을 전담하는 서비스 클래스
@Service
public class MemberService {

    private final UserMapper userMapper;

    // DB에 날것의 비밀번호가 털리지 않게 막아주기 위한 해시 암호화 객체
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 임시 비밀번호 메일을 보낼 때 사용하는 스프링 기본 메일 센더
    // application.yml에 구글 SMTP 세팅을 해뒀기 때문에 그걸 인식해서 주입됨
    private final JavaMailSender mailSender;

    public MemberService(UserMapper userMapper, JavaMailSender mailSender) {
        this.userMapper = userMapper;
        this.mailSender = mailSender;
    }

    // ===== 회원가입 관련 =====

    // 각 필터별 중복 검사 로직
    // count 쿼리를 날려서 결과값이 0개면 안 쓴다는 뜻이니 사용 가능(true)으로 판단함
    public boolean isIdAvailable(String id) {
        return userMapper.countById(id) == 0;
    }

    public boolean isEmailAvailable(String email) {
        return userMapper.countByEmail(email) == 0;
    }

    public boolean isPhoneNumAvailable(String phoneNum) {
        return userMapper.countByPhoneNum(phoneNum) == 0;
    }

    // 최종 회원가입 진행 메서드
    public void register(MemberDTO member) {
        // 클라이언트(프론트)에서 중복검사를 뚫고 왔더라도 누군가 API를 직접 찌를 수 있으므로
        // insert 직전에 서버단에서 "무조건" 한 번 더 중복을 철저하게 방어함
        if (!isIdAvailable(member.getId())) {
            throw new RuntimeException("이미 사용 중인 아이디입니다.");
        }
        if (!isEmailAvailable(member.getEmail())) {
            throw new RuntimeException("이미 사용 중인 이메일입니다.");
        }
        if (!isPhoneNumAvailable(member.getPhoneNum())) {
            throw new RuntimeException("이미 사용 중인 전화번호입니다.");
        }

        // 우리 시스템의 고유한 회원 식별값(MemberCode)을 만들어야 함
        // 가장 최근에 가입한 사람의 코드(예: U000000010)를 DB에서 가져온 다음
        // 그 번호에 +1을 해서 순차적으로 새 번호(U000000011)를 부여하는 방식
        String lastCode = userMapper.findLastMemberCode();
        String newCode = generateNextCode(lastCode);
        member.setMemberCode(newCode);

        // 보안의 기본: 평문 비밀번호를 그대로 DB에 넣으면 법에 걸림! 무조건 BCrypt로 솔팅 암호화 진행
        member.setPassword(passwordEncoder.encode(member.getPassword()));

        // 회원의 타입(일반회원 U, 관리자 A 등) 지정
        member.setUserType("U");

        // 알림 상태 안 넘어왔으면 기본값으로 Y(동의) 셋팅
        if (member.getNotificationStatus() == null) {
            member.setNotificationStatus("Y");
        }

        // 정상 활동중이라는 표식 (Y). 만약 이 사람이 탈퇴하면 N으로 상태값만 바뀌게 됨
        member.setMemberYn("Y");

        // Mapper(마이바티스) 호출해서 진짜 DB 레코드로 생성함
        int result = userMapper.insertMember(member);
        if (result == 0) {
            throw new RuntimeException("회원가입 실패");
        }
    }

    // 회원 코드 순차 자동 생성 헬퍼
    // 문자열 파싱 로직: U 떼내고 숫자만 발라내서 1 더한 뒤 %09d 형식으로 다시 9자리 0으로 꽉 채워서 문자열 덧붙임
    private String generateNextCode(String lastCode) {
        // 첫 가입자라면 기본 시작 번호로 세팅
        if (lastCode == null)
            return "U000000001";
        int num = Integer.parseInt(lastCode.substring(1)) + 1;
        return String.format("U%09d", num);
    }

    // ===== 마이페이지 / 회원정보 관리 =====

    // 토큰에서 파싱한 아이디로 내 정보 전체를 싹 다 다시 불러오는 메서드
    public MemberDTO getMemberInfoById(String id) {
        MemberDTO dto = userMapper.findById(id);

        if (dto == null) {
            throw new RuntimeException("존재하지 않는 회원입니다.");
        }

        // 중요 로직: DB 데이터는 아직 안 지워지고 남아있어도 member_yn이 'N' 이라면 탈퇴한 사람이니 없는 사람 취급해야 함
        if (!"Y".equals(dto.getMemberYn())) {
            throw new RuntimeException("탈퇴된 회원입니다.");
        }
        return dto;
    }

    // 정보 수정 처리 (이메일, 주소 등 변경내역 엎어치기)
    public void updateMember(MemberDTO dto) {
        int result = userMapper.updateMember(dto);
        if (result == 0) {
            throw new RuntimeException("회원정보 수정 실패");
        }
    }

    // 알림 여부 토글 값만 변경하는 전용 분리 기능
    public void updateNotification(String memberCode, boolean enabled) {
        int result = userMapper.updateNotification(memberCode, enabled ? "Y" : "N");
        if (result == 0) {
            throw new RuntimeException("SNS 알림 설정 변경 실패");
        }
    }

    // 회원 탈퇴 - (소프트 딜리트)
    // 앞서 말했듯이 행을 날리지 않음. update 쿼리 날려서 member_yn 컬럼만 쏙 바꿔버림
    public void withdrawMember(String memberCode) {
        int result = userMapper.withdrawMember(memberCode);
        if (result == 0) {
            throw new RuntimeException("회원 탈퇴 실패");
        }
    }

    // 아이디 찾기 - 이름과 이메일 둘 다 맞아야 찾을 수 있도록 조건을 걸어둠 (타인이 유추하기 어렵게 함)
    // 찾으면 DB 조회 결과의 ID 속성을 리턴
    public String findIdByNameAndEmail(String name, String email) {
        MemberDTO member = userMapper.findByNameAndEmail(name, email);
        if (member == null)
            throw new RuntimeException("일치하는 회원이 없습니다.");
        return member.getId();
    }

    // 비밀번호 분실 시: 새 비밀번호 찾기 (비밀번호 리셋 + 이메일 전송)
    // 원래 비밀번호는 해시되어있어서 관리자도 못까봄. 무조건 랜덤 번호로 새로 밀어버리고 알려주는 수밖에 없음
    public void sendTempPassword(String id, String email) {

        // 아이디와 이메일이 매치되는 사람을 먼저 검증
        MemberDTO member = userMapper.findByIdAndEmail(id, email);
        if (member == null)
            throw new RuntimeException("입력 정보와 일치하는 회원이 없습니다.");

        // 랜덤 10자리 숫자 비밀번호 만들기
        String tempPwd = generateTempPassword();

        // 만든 임시 비번도 당연히 똑같이 암호화해서 DB에 교체해줘야 로그인 시 인증이 가능함
        String encodedPwd = passwordEncoder.encode(tempPwd);

        // 딱 Password 하나만 바꿀 거니까 update 쿼리 분리해서 성능 방어 및 사이드 이펙트 방지
        userMapper.updatePassword(member.getMemberCode(), encodedPwd);

        // 자바 내장 메일 모듈을 이용해서 사용자의 이메일함으로 임시비밀번호 평문을 발송함
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[훌라후프] 임시 비밀번호 발급 안내");
        message.setText("임시 비밀번호: " + tempPwd + "\n로그인 후 반드시 내 정보에서 비밀번호를 변경해주세요.");
        mailSender.send(message);
    }

    // 내 정보 창 안에서 "비밀번호 변경" 메뉴 썼을 때
    public void changePassword(String id, String currentPwd, String newPwd) {

        MemberDTO member = userMapper.findById(id);
        if (member == null)
            throw new RuntimeException("회원 정보를 찾을 수 없습니다.");

        // 지금 비밀번호를 맞게 썼는지 확인 (이거 안 받으면 자리에 없는 틈을 타서 누가 비번 바꿔버릴 수 있음)
        if (!passwordEncoder.matches(currentPwd, member.getPassword())) {
            throw new RuntimeException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 검증 뚫었으면 안전하게 새 비밀번호를 암호화해서 교체 완료
        String encodedNewPwd = passwordEncoder.encode(newPwd);
        userMapper.updatePassword(member.getMemberCode(), encodedNewPwd);
    }

    // 대충 0~9 자리의 난수를 발생시키는 단순 임시번호 생성식 (추후 UUID나 영어 혼합 등으로 올려칠 수 있음)
    private String generateTempPassword() {
        return Long.toString((long) (Math.random() * 10000000000L)); // 10자리 랜덤 숫자
    }
}
