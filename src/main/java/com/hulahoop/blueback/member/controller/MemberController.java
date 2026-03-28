package com.hulahoop.blueback.member.controller;

import com.hulahoop.blueback.member.model.dto.MemberDTO;
import com.hulahoop.blueback.member.model.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 회원의 가입, 정보 조회, 수정, 탈퇴 등 회원과 관련된 모든 요청을 받는 컨트롤러
@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 신규 회원가입 버튼을 눌렀을 때 호출
    // 비밀번호 해싱이나 중복 체크 등 복잡한 건 서비스가 알아서 처리하니까, 컨트롤러는 예외가 안 터지면 200 OK만 내려줌
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

    // 회원가입 전 필수 관문인 '아이디 중복 확인' API
    // 이 경로는 아직 로그인을 안 한 사람도 찔러봐야 하므로 JwtFilter의 PUBLIC_PATHS 쪽에 등록되어 있음
    // DB에 아이디가 없으면 available: true 리턴
    @GetMapping("/check-id")
    public ResponseEntity<?> checkId(@RequestParam String id) {
        boolean available = memberService.isIdAvailable(id);
        return ResponseEntity.ok(Map.of("available", available));
    }

    // 동일하게 이메일이 이미 존재하는지 확인
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        boolean available = memberService.isEmailAvailable(email);
        return ResponseEntity.ok(Map.of("available", available));
    }

    // 전화번호도 하나당 계정 하나만 허용하기 위해 중복 확인
    @GetMapping("/check-phone")
    public ResponseEntity<?> checkPhone(@RequestParam String phoneNum) {
        boolean available = memberService.isPhoneNumAvailable(phoneNum);
        return ResponseEntity.ok(Map.of("available", available));
    }

    // 아이디 찾기 - 프론트에서 넘어온 이름과 이메일 세트가 DB와 맞으면 아이디를 까서 보여줌
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

    // 비밀번호 재설정 - 아이디랑 이메일이 맞으면 사용자의 비밀번호를 임의의 난수로 바꿔버리고 그 번호를 메일로 쏴줌
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

    // --- 여기서부터 나오는 API들은 전부 JWT 토큰이 필수로 있어야 통과되는 경로들 ---

    // 내 마이페이지 클릭 시 내 정보 가져오기
    // 스프링 시큐리티에서 로그인(토큰 검증)된 사용자 정보를 Authentication 객체로 알아서 주입해줌
    // 우리가 굳이 헤더에서 토큰 까고 파싱할 필요가 없음! (이미 필터가 다 해놓은 결과물)
    @GetMapping("/info")
    public ResponseEntity<?> getMemberInfo(Authentication authentication) {
        // 혹시 모르니 빈 값이면 보안 에러 처리
        if (authentication == null) {
            return ResponseEntity.status(403).body("인증되지 않은 요청입니다.");
        }

        // 토큰 발급할 때 Subject에 아이디(username)를 넣어놨기 때문에 getName()을 하면 아이디가 딸려옴
        String id = authentication.getName();
        System.out.println("[MemberController] 인증된 사용자 ID: " + id);

        // 내 DB에서 최신 정보 조회해서 프론트로 전송
        MemberDTO member = memberService.getMemberInfoById(id);
        if (member == null) {
            return ResponseEntity.status(404).body("회원 정보를 찾을 수 없습니다.");
        }

        return ResponseEntity.ok(member);
    }

    // 내 정보 수정 요청
    @PatchMapping("/update")
    public ResponseEntity<?> updateMember(@RequestBody MemberDTO dto, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(403).body("인증되지 않은 요청입니다.");
        }

        // 클라이언트가 바디값(dto) 안에 남의 id를 조작해서 보낼 수도 있음
        // 그래서 바디의 id는 싹 무시해버리고, 오직 '현재 로그인된 토큰'에서 나온 id(authentication.getName())만 확실히
        // 믿고 덮어씌움
        String id = authentication.getName();
        System.out.println("[MemberController] 회원정보 수정 요청 ID: " + id);

        // 업데이트 쿼리 칠 때 기준이 될 고유키(memberCode)를 맞추려고 기존 정보를 다시 한 번 불러와서 안전하게 세팅함
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

    // 비밀번호 수정 (내가 로그인 중일 때 내 비번 바꾸는 기능)
    // 기존 비밀번호를 한 번 더 물어보고 맞으면 그때 새 비번으로 덮어씀
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

    // 회원 탈퇴 버튼
    // 보통 실서비스에서는 사용자 데이터를 진짜 DELETE로 날리지 않음 (나중에 증빙자료나 복구를 대비)
    // 그래서 상태값(member_yn)만 'N'으로 바꿔버리는 '소프트 딜리트' 처리를 주로 함
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteMember(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(403).body("인증되지 않은 요청입니다.");
        }

        String id = authentication.getName();
        MemberDTO existing = memberService.getMemberInfoById(id);

        try {
            // memberCode를 기준으로 DB에서 member_yn 상태를 'N'으로 바꾸러 서비스 호출
            memberService.withdrawMember(existing.getMemberCode());
            return ResponseEntity.ok("회원 탈퇴 완료");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("회원 탈퇴 실패: " + e.getMessage());
        }
    }
}
