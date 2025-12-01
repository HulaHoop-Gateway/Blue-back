package com.hulahoop.blueback.history.model.service;

import com.hulahoop.blueback.history.model.dao.HistoryMapper;
import com.hulahoop.blueback.history.model.dto.HistoryResponseDto;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistoryService {

    private final HistoryMapper historyMapper;
    private final RestTemplate restTemplate;

    public HistoryService(HistoryMapper historyMapper, RestTemplate restTemplate) {
        this.historyMapper = historyMapper;
        this.restTemplate = restTemplate;
    }

    public List<HistoryResponseDto> getTransactionHistory(String memberCode, String status) {
        return historyMapper.findHistoryByMemberCode(memberCode, status);
    }

    public Map<String, Object> cancelReservation(Long transactionNum) {
        System.out.println("[HistoryService] cancelReservation 진입 - transactionNum: " + transactionNum);
        Map<String, Object> result = new HashMap<>();

        // 1️⃣ 트랜잭션 정보 조회
        HistoryResponseDto transaction = historyMapper.findTransactionByNum(transactionNum);
        System.out.println("[HistoryService] 조회된 트랜잭션: " + transaction);
        if (transaction == null) {
            result.put("success", false);
            result.put("message", "해당 예약을 찾을 수 없습니다.");
            return result;
        }

        // 2️⃣ 이미 취소된 예약인지 확인
        if ("R".equals(transaction.getStatus())) {
            result.put("success", false);
            result.put("message", "이미 취소된 예약입니다.");
            return result;
        }

        // 3️⃣ merchant_code 기반으로 intent 결정
        String merchantCode = transaction.getMerchantCode();
        System.out.println("[HistoryService] merchantCode: " + merchantCode);
        String intent = null;

        if (merchantCode != null && merchantCode.startsWith("M")) {
            intent = "movie_cancel";
        } else if (merchantCode != null && merchantCode.startsWith("B")) {
            intent = "bike_cancel";
        } else {
            result.put("success", false);
            result.put("message", "알 수 없는 가맹점 코드입니다: " + merchantCode);
            return result;
        }

        // 4️⃣ 게이트웨이로 취소 요청 (POST + intent 헤더)
        try {
            // Gateway의 Path=/api/gateway/** predicate와 매칭되도록 하위 경로 추가
            String gatewayUrl = "http://localhost:8080/api/gateway/cancel";

            // BikeController가 기대하는 형식: { "intent": "...", "data": { ... } }
            Map<String, Object> data = new HashMap<>();
            data.put("transactionNum", transactionNum);
            data.put("memberCode", transaction.getMemberCode());
            data.put("amountUsed", transaction.getAmountUsed());
            data.put("startDate", transaction.getStartDate());
            data.put("endDate", transaction.getEndDate());

            Map<String, Object> cancelRequest = new HashMap<>();
            cancelRequest.put("intent", intent); // BikeController가 바디에서 읽음
            cancelRequest.put("data", data); // 실제 데이터는 data 래퍼 안에

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("intent", intent); // Gateway 라우팅용

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(cancelRequest, headers);

            restTemplate.postForEntity(gatewayUrl, entity, String.class);

            result.put("success", true);
            result.put("message", "예약이 성공적으로 취소되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "취소 처리 중 오류가 발생했습니다: " + e.getMessage());
        }

        return result;
    }
}