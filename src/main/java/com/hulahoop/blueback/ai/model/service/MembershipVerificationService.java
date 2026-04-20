package com.hulahoop.blueback.ai.model.service;

import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

// 블루백(메인 서버)이 가진 회원 DB 정보(전화번호 등)를 바탕으로,
// 이 사용자가 영화나 자전거 서비스 쪽에 가입되어 있는지를 게이트웨이를 통해 교차 검증하는 서비스
@Service
public class MembershipVerificationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserMapper userMapper;

    // 모든 MSA 간 통신은 게이트웨이(8080)를 거치는 게 원칙임
    @Value("${gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    public MembershipVerificationService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    // 1단계: 토큰에서 추출한 아이디로 내 DB(Blue)부터 까서 핸드폰 번호를 가져옴
    // 이 번호가 다른 서비스들에서 공통 식별 마스터키 역할을 하게 됨
    public String getUserPhoneNumber(String userId) {
        MemberDTO member = userMapper.findById(userId);
        if (member == null) {
            return null;
        }
        return member.getPhoneNum();
    }

    // 2-1단계: 영화관 서비스에 가입된 놈인지 물어보기
    // 헤더에 intent를 "movie_member_check"로 실어 보내면, 게이트웨이가 영화 서버로 토스해줌
    public boolean isCinemaMember(String phoneNumber) {
        return checkMember(phoneNumber, "movie_member_check", "영화관");
    }

    // 2-2단계: 자전거 서비스에 가입된 놈인지 물어보기 (intent: "bike_member_check")
    public boolean isBikeMember(String phoneNumber) {
        return checkMember(phoneNumber, "bike_member_check", "자전거");
    }

    // 3단계: 진짜로 HTTP 요청을 날리는 공통 헬퍼 메서드
    private boolean checkMember(String phoneNumber, String headerIntent, String serviceName) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }

        try {
            // 목적지는 무조건 게이트웨이의 /dispatch 엔드포인트
            String url = gatewayUrl + "/api/gateway/dispatch";

            // Body 셋팅 - 대상 서비스(영화, 자전거) 라우터에 도착했을 때, "나는 멤버십 체크하러 온 놈이다"라고 알려주기 위한 내부
            // intent 값
            Map<String, Object> payload = new HashMap<>();
            payload.put("intent", "member_check");

            Map<String, Object> data = new HashMap<>();
            data.put("phone", phoneNumber);
            payload.put("data", data);

            // Headers 셋팅 -  게이트웨이가 이 헤더를 보고 길안내(라우팅)를 함 
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("intent", headerIntent); // 예: "movie_member_check"

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(payload, headers),
                    Map.class);

            // 상대방 서버에서 "응, 걔 우리 회원 맞아 (exists: true)" 라고 답변이 오면 성공
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Boolean.TRUE.equals(response.getBody().get("exists"));
            }
        } catch (Exception e) {
            // 통신 에러 터져도 무조건 안 되는 걸로 닫아버림 (보안)
            System.err.println(serviceName + " 회원 조회 실패: " + e.getMessage());
        }

        return false;
    }
}
