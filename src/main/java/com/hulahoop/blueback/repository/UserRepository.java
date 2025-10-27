package com.hulahoop.blueback.repository;

import com.hulahoop.blueback.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Member, String> {
    // PK(member_code) 기준 조회는 JpaRepository가 기본 제공
    Optional<Member> findByUsername(String username); // username(id 컬럼) 기준 조회
}
