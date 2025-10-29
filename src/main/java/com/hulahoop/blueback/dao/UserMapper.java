package com.hulahoop.blueback.dao;

import com.hulahoop.blueback.dto.MemberDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    // username(id)으로 회원 정보 조회
    MemberDTO findByUsername(@Param("username") String username);

    // 회원 코드로 회원 조회
    MemberDTO findByMemberCode(@Param("memberCode") String memberCode);

    // 회원 등록
    int insertMember(MemberDTO member);

    // 회원 정보 수정
    int updateMember(MemberDTO member);

    // 회원 삭제
    int deleteMember(@Param("memberCode") String memberCode);
}
