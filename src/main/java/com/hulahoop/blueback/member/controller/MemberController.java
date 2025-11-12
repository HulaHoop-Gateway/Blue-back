package com.hulahoop.blueback.member.controller;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import com.hulahoop.blueback.member.model.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
@CrossOrigin(origins = "http://localhost:5173")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // ✅ 회원정보 조회
    @GetMapping("/info")
    public ResponseEntity<?> getMemberInfo(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(403).body("인증되지 않은 요청입니다.");
        }

        String id = authentication.getName();
        System.out.println("[MemberController] 인증된 사용자 ID: " + id);

        MemberDTO member = memberService.getMemberInfoById(id);
        if (member == null) {
            return ResponseEntity.status(404).body("회원 정보를 찾을 수 없습니다.");
        }

        return ResponseEntity.ok(member);
    }

    // ✅ 회원정보 수정 (PATCH)
    @PatchMapping("/update")
    public ResponseEntity<?> updateMember(@RequestBody MemberDTO dto, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(403).body("인증되지 않은 요청입니다.");
        }

        String id = authentication.getName();
        System.out.println("[MemberController] 회원정보 수정 요청 ID: " + id);

        // 로그인한 사용자 정보 확인
        MemberDTO existing = memberService.getMemberInfoById(id);
        dto.setId(id);
        dto.setMemberCode(existing.getMemberCode());

        try {
            memberService.updateMember(dto);
            return ResponseEntity.ok("회원정보 수정 완료");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("회원정보 수정 실패: " + e.getMessage());
        }
    }

    // ✅ 회원 탈퇴
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteMember(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(403).body("인증되지 않은 요청입니다.");
        }

        String id = authentication.getName();
        MemberDTO existing = memberService.getMemberInfoById(id);

        try {
            memberService.withdrawMember(existing.getMemberCode());
            return ResponseEntity.ok("회원 탈퇴 완료");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("회원 탈퇴 실패: " + e.getMessage());
        }
    }
}
