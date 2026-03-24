package com.hulahoop.blueback.ai.model.service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

// 토스페이먼츠 코어 연동 로직
// 백엔드 서버끼리의 통신을 통해 실제 돈이 오가는 결제 승인(Confirm) 과정을 처리함
@Service
public class TossPaymentService {

        // application.yml 등에 숨겨져 있는 토스페이먼츠 '시크릿 키'
        // 프론트엔드용 '클라이언트 키'랑 다름! 이건 서버에만 둬야 하는 일급비밀임. 유출되면 남이 결제 취소시키고 난리남
        @Value("${toss.secret.key}")
        private String secretKey;

        private final RestTemplate restTemplate = new RestTemplate();

        // 1단계 결제생성 (현재는 더미 리턴)
        public Map<String, Object> createPayment(String orderId, long amount, String orderName) {
                return Map.of(
                                "orderId", orderId,
                                "amount", amount,
                                "orderName", orderName);
        }

        // 2단계: 프론트가 떠넘긴 paymentKey를 들고 진짜 토스 본사 서버로 쳐들어가는 부분
        public Map<String, Object> confirmPayment(String paymentKey, String orderId, long amount) {

                // 토스 API 문서를 보면 "시크릿키 뒤에 콜론(:)을 붙여서 Base64로 인코딩한 값을 써라" 라고 되어있음
                // 왜 콜론을 붙이냐면 HTTP Basic Auth 규격 자체가 "아이디:비밀번호" 형태인데,
                // 토스는 아이디 자리에 시크릿키를 쓰고 비밀번호는 빈칸으로 냅두는 구조를 쓰기 때문임
                String credentials = secretKey + ":";

                // 자바 내장 기능을 써서 문자열을 Base64 형태(알아보기 힘든 문자열)로 꼬아버림
                String encodedAuth = Base64.getEncoder()
                                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

                // 헤더 셋팅
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                // "Authorization" : "Basic dXNlcl9zZWNyZXRfa2V5Og==" 처럼 셋팅됨
                headers.set("Authorization", "Basic " + encodedAuth);

                // 토스가 요구하는 세 가지 필수 값을 JSON 바디로 뭉침
                Map<String, Object> body = Map.of(
                                "paymentKey", paymentKey,
                                "orderId", orderId,
                                "amount", amount);

                // 헤더랑 바디 합체
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                // 드디어 토스 결제 승인 API로 POST 요청을 쏨!! (동기식)
                // 만약 이 요청이 성공하면(200 OK) 드디어 고객 통장에서 돈이 빠져나가고,
                // 실패하면 잔액부족, 한도초과 등의 에러 코드를 뱉게 됨
                ResponseEntity<Map> response = restTemplate.postForEntity(
                                "https://api.tosspayments.com/v1/payments/confirm",
                                entity,
                                Map.class);

                // 전체 JSON(결제 완료 시간, 승인 번호, 영수증 링크 등)을 받아서 돌려줌
                return response.getBody();
        }
}
