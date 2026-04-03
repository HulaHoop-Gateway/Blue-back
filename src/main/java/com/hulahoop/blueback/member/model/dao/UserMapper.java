package com.hulahoop.blueback.member.model.dao;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// MyBatis 프레임워크가 이 인터페이스를 보고 자동으로 구현체를 만들어주는 곳 (DAO 역할)
// 실제 SQL 쿼리문은 resources/mapper/UserMapper.xml 파일 안에 적혀있고, 메서드 이름이 XML의 id값과 매칭됨
// @Mapper: 마이바티스가 이 어노테이션을 보고 "아, DB랑 통신하는 껍데기 인터페이스구나!" 하고 인식함. 
// 이거 하나만 달아두면 우리가 직접 복잡한 JDBC 코드를 짤 필요 없이, 스프링이 뒤에서 XML 쿼리문과 자바 메서드를 1:1로 매핑해주는 구현체를 자동으로 생성해 줌.
@Mapper
public interface UserMapper {

    // @Param: 자바 단에서 넘긴 변수(id)가 XML 쿼리문 안에서 #{id} 처럼 똑바로 매칭해서 꺼내 쓸 수 있도록 데이터에 명찰을
    // 달아주는 어노테이션임.
    // 아이디(userId)를 조건으로 회원 테이블(member) 단건 조회
    MemberDTO findById(@Param("id") String id);

    // 내부 관리용 '회원 코드(U00000001 등)'를 조건으로 단건 조회
    MemberDTO findByMemberCode(@Param("memberCode") String memberCode);

    // 회원가입할 때 폼 데이터 받아서 insert (비밀번호는 반드시 암호화된 상태여야 함)
    int insertMember(MemberDTO member);

    // 마이페이지에서 내 정보(전화번호, 주소 등) 수정할 때 호출
    int updateMember(MemberDTO member);

    // 이메일 수신 동의 여부('Y' or 'N')만 단독으로 껐다 켰다 할 때 사용
    int updateNotification(@Param("memberCode") String memberCode,
            @Param("status") String status);

    // 회원 탈퇴 처리
    // 실제로 DB에서 DELETE 쿼리를 날려버리면 그 회원의 과거 결제 내역이나 예약 내역들이 무결성 제약조건에 걸려 터지거나,
    // 데이터 통계가 박살나기 때문에 'member_yn' 플래그만 'N'으로 바꾸는 "소프트 딜리트(Soft Delete)" 방식을 사용함
    int withdrawMember(@Param("memberCode") String memberCode);

    // 회원가입 시 아이디 중복 체크 (결과가 1 이상이면 이미 있는 놈)
    int countById(@Param("id") String id);

    // 새로운 회원이 가입할 때 부여할 다음 회원 번호(예: U000000010)를 만들기 위해,
    // 현재 DB에 저장된 가장 마지막(최신) 코드를 역순으로 정렬해서 1개만 뽑아오는 역할
    String findLastMemberCode();

    // 이메일 중복 체크 (보안상 한 이메일로 여러 계정 못 만들게 함)
    int countByEmail(@Param("email") String email);

    // 핸드폰 번호 중복 체크 (이 번호가 나중에 자전거/영화 MSA 연동할 때 마스터키가 됨)
    int countByPhoneNum(@Param("phoneNum") String phoneNum);

    // 아이디 찾기: 이름이랑 이메일이 동시에 일치하는 사람 찾기
    MemberDTO findByNameAndEmail(@Param("name") String name, @Param("email") String email);

    // 비밀번호 찾기: 자기가 누군지 주장하는 아이디와, 가입할 때 썼던 이메일이 진짜 일치하는지 증명
    MemberDTO findByIdAndEmail(@Param("id") String id, @Param("email") String email);

    // 비밀번호 재설정 (암호화된 새 비밀번호로 덮어쓰기)
    int updatePassword(@Param("memberCode") String memberCode, @Param("password") String password);
}
