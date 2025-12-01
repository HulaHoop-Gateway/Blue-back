package com.hulahoop.blueback.member.controller;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import com.hulahoop.blueback.member.model.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerMember(@RequestBody MemberDTO dto) {
        try {
            memberService.register(dto);
            return ResponseEntity.ok("회원가입 성공");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("회원가입 중 오류가 발생했습니다.");
        }
    }

    // ✅ [1] 아이디 중복 확인 (비회원 접근 허용)
    @GetMapping("/check-id")
    public ResponseEntity<?> checkId(@RequestParam String id) {
        boolean available = memberService.isIdAvailable(id);
        return ResponseEntity.ok(Map.of("available", available));
    }

    // ✅ [추가] 이메일 중복 확인
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        boolean available = memberService.isEmailAvailable(email);
        return ResponseEntity.ok(Map.of("available", available));
    }

    // ✅ [추가] 전화번호 중복 확인
    @GetMapping("/check-phone")
    public ResponseEntity<?> checkPhone(@RequestParam String phoneNum) {
        boolean available = memberService.isPhoneNumAvailable(phoneNum);
        return ResponseEntity.ok(Map.of("available", available));
    }

    @PostMapping("/find-id")
    public ResponseEntity<?> findId(@RequestBody Map<String, String> param) {
        String name = param.get("name");
        String email = param.get("email");
        try {
            String id = memberService.findIdByNameAndEmail(name, email);
            return ResponseEntity.ok(Map.of("id", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> param) {
        String id = param.get("id");
        String email = param.get("email");
        try {
            memberService.sendTempPassword(id, email);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ [2] 회원정보 조회 (로그인 필요)
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

    // ✅ [3] 회원정보 수정
    @PatchMapping("/update")
    public ResponseEntity<?> updateMember(@RequestBody MemberDTO dto, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(403).body("인증되지 않은 요청입니다.");
        }

        String id = authentication.getName();
        System.out.println("[MemberController] 회원정보 수정 요청 ID: " + id);

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

    // ✅ [3-1] 비밀번호 변경
    @PatchMapping("/update-password")
    public ResponseEntity<?> updatePassword(@RequestBody Map<String, String> param, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(403).body("인증되지 않은 요청입니다.");
        }

        String id = authentication.getName();
        String currentPwd = param.get("currentPassword");
        String newPwd = param.get("newPassword");

        try {
            memberService.changePassword(id, currentPwd, newPwd);
            return ResponseEntity.ok("비밀번호 변경 완료");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("비밀번호 변경 중 오류가 발생했습니다.");
        }
    }

    // ✅ [4] 회원 탈퇴
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
