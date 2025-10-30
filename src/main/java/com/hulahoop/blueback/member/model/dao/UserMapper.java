package com.hulahoop.blueback.member.model.dao;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    // ✅ id로 회원 정보 조회
    MemberDTO findById(@Param("id") String id);

    // ✅ 회원 코드로 회원 조회
    MemberDTO findByMemberCode(@Param("memberCode") String memberCode);

    // ✅ 회원 등록 (회원가입)
    int insertMember(MemberDTO member);

    // ✅ 회원 정보 수정
    int updateMember(MemberDTO member);

    // ✅ 회원 삭제
    int deleteMember(@Param("memberCode") String memberCode);

    // ✅ 아이디 중복 확인 (회원가입용)
    int countById(@Param("id") String id);

    // ✅ 마지막 회원코드 조회 (회원가입용)
    String findLastMemberCode();
}
