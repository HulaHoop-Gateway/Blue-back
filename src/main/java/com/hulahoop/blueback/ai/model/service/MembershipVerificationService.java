package com.hulahoop.blueback.ai.model.service;

import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class MembershipVerificationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserMapper userMapper;

    // 게이트웨이 포트 8080
    @Value("${gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    public MembershipVerificationService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * Blue 사용자의 전화번호 조회
     */
    public String getUserPhoneNumber(String userId) {
        MemberDTO member = userMapper.findById(userId);
        if (member == null) {
            return null;
        }
        return member.getPhoneNum();
    }

    /**
     * 영화관 회원 여부 확인
     * Gateway 라우팅 조건: Path=/api/gateway/**, Header=intent 포함 .*movie.*
     */
    public boolean isCinemaMember(String phoneNumber) {
        // Header에 'movie'가 포함된 intent를 보내야 Gateway가 영화 서비스로 라우팅함
        return checkMember(phoneNumber, "movie_member_check", "영화관");
    }

    /**
     * 자전거 회원 여부 확인
     * Gateway 라우팅 조건: Path=/api/gateway/**, Header=intent 포함 .*bike.*
     */
    public boolean isBikeMember(String phoneNumber) {
        // Header에 'bike'가 포함된 intent를 보내야 Gateway가 자전거 서비스로 라우팅함
        return checkMember(phoneNumber, "bike_member_check", "자전거");
    }

    /**
     * 공통 회원 확인 로직
     * @param headerIntent Gateway 라우팅을 위한 HTTP Header 값
     */
    private boolean checkMember(String phoneNumber, String headerIntent, String serviceName) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }

        try {
            // Gateway 설정에 따라 /api/gateway/** 경로로 요청
            String url = gatewayUrl + "/api/gateway/dispatch";
            
            // Body: 실제 서비스(NovaCinema/Bikeway)가 처리할 데이터
            Map<String, Object> payload = new HashMap<>();
            payload.put("intent", "member_check"); // 서비스 내부 로직용 intent
            
            Map<String, Object> data = new HashMap<>();
            data.put("phone", phoneNumber);
            payload.put("data", data);

            // Headers: Gateway 라우팅용 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("intent", headerIntent); // Gateway 라우팅 핵심

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url, 
                new HttpEntity<>(payload, headers), 
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Boolean.TRUE.equals(response.getBody().get("exists"));
            }
        } catch (Exception e) {
            System.err.println(serviceName + " 회원 조회 실패: " + e.getMessage());
        }
        
        return false;
    }
}
