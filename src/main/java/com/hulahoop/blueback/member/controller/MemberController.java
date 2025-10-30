package com.hulahoop.blueback.member.controller;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import com.hulahoop.blueback.member.model.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/member") // ✅ /api/member 로 변경
@CrossOrigin(origins = "http://localhost:5173")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // ✅ 아이디 중복확인
    @GetMapping("/check-id")
    public ResponseEntity<?> checkId(@RequestParam("id") String id) {
        System.out.println("🟦 [MemberController] /api/member/check-id 호출됨, id = " + id);

        boolean available = memberService.isIdAvailable(id);

        System.out.println("🟩 [MemberController] 사용 가능 여부 = " + available);

        return ResponseEntity.ok(Map.of("available", available));
    }

    // ✅ 회원가입
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody MemberDTO member) {
        System.out.println("🟦 [MemberController] /api/member/signup 호출됨");
        System.out.println("➡️ 전달된 회원 데이터: " + member);

        memberService.register(member);

        System.out.println("🟩 [MemberController] 회원가입 완료: " + member.getId());

        return ResponseEntity.ok(Map.of("message", "회원가입이 완료되었습니다."));
    }
}
