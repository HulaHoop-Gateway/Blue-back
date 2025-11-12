package com.hulahoop.blueback.member.model.dao;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    // ✅ ID로 회원 조회
    MemberDTO findById(@Param("id") String id);

    // ✅ 회원 코드로 회원 조회
    MemberDTO findByMemberCode(@Param("memberCode") String memberCode);

    // ✅ 회원 등록
    int insertMember(MemberDTO member);

    // ✅ 회원 정보 수정
    int updateMember(MemberDTO member);

    // ✅ SNS 알림 설정 변경
    int updateNotification(@Param("memberCode") String memberCode,
                           @Param("status") String status);

    // ✅ 회원 탈퇴 (삭제 X → 플래그 변경)
    int withdrawMember(@Param("memberCode") String memberCode);

    // ✅ 아이디 중복 확인
    int countById(@Param("id") String id);

    // ✅ 마지막 회원 코드 조회
    String findLastMemberCode();
}
