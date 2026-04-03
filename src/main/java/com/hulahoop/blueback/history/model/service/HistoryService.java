package com.hulahoop.blueback.history.model.service;

import com.hulahoop.blueback.history.model.dao.HistoryMapper;
import com.hulahoop.blueback.history.model.dto.HistoryResponseDto;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 마이페이지 사용 내역(영화, 자전거 짬뽕데이터) 조회 및 공통 '취소' 로직을 다루는 핵심 서비스
@Service
public class HistoryService {

    private final HistoryMapper historyMapper;
    private final RestTemplate restTemplate;

    public HistoryService(HistoryMapper historyMapper, RestTemplate restTemplate) {
        this.historyMapper = historyMapper;
        this.restTemplate = restTemplate;
    }

    // 단순 SELECT 쿼리 호출 - 조건에 맞는 결제/예약 내역 리스트 리턴
    public List<HistoryResponseDto> getTransactionHistory(String memberCode, String status) {
        return historyMapper.findHistoryByMemberCode(memberCode, status);
    }

    // 마이페이지에서 통합된 '취소' 버튼을 눌렀을 때 일어나는 복잡한 4단계 로직
    public Map<String, Object> cancelReservation(Long transactionNum) {
        System.out.println("[HistoryService] cancelReservation 진입 - transactionNum: " + transactionNum);

        // 프론트랑 통신할 결과 바구니 준비
        Map<String, Object> result = new HashMap<>();

        // 단계 1: 진짜 존재하는 주문번호인지 확인부터 함 (남의 번호 막 넣어서 찌를 수 있으니까)
        HistoryResponseDto transaction = historyMapper.findTransactionByNum(transactionNum);
        System.out.println("[HistoryService] 조회된 트랜잭션: " + transaction);
        if (transaction == null) {
            result.put("success", false);
            result.put("message", "해당 예약을 찾을 수 없습니다.");
            return result;
        }

        // 단계 2: 누르고 또 누르는 '따닥' 방지. 상태값이 이미 환불(Refund - 'R')이면 차단함
        if ("R".equals(transaction.getStatus())) {
            result.put("success", false);
            result.put("message", "이미 취소된 예약입니다.");
            return result;
        }

        // 단계 3: 이 주문 내역이 '영화'인지 '자전거'인지 판단해야 함
        // 우리 시스템의 규칙: 결제할 때 만든 가맹점코드(merchantCode)가 'M'으로 시작하면 영화(Movie), 'B'로 시작하면
        // 자전거(Bike)임
        String merchantCode = transaction.getMerchantCode();
        System.out.println("[HistoryService] merchantCode: " + merchantCode);

        // 헤더에 태울 인텐트값 초기화
        String intent = null;

        if (merchantCode != null && merchantCode.startsWith("M")) {
            // M으로 시작하면 영화 쪽으로 라우팅되도록 "movie_cancel" 인텐트 부여
            intent = "movie_cancel";
        } else if (merchantCode != null && merchantCode.startsWith("B")) {
            // B로 시작하면 자전거 쪽으로 라우팅되도록 "bike_cancel" 인텐트 부여
            intent = "bike_cancel";
        } else {
            // 정체불명 코드의 사고 방지
            result.put("success", false);
            result.put("message", "알 수 없는 가맹점 코드입니다: " + merchantCode);
            return result;
        }

        // 단계 4: 어떤 서비스(서버)가 취소처리를 하든간에, 우리는 게이트웨이(8080)로 POST 요청을 하나 날려서 짬처리 시키면 됨
        try {
            // Gateway의 cancel 전용 경로 (의도를 명확히 하기 위해 dispatch와 분리해둔 것)
            String gatewayUrl = "http://localhost:8080/api/gateway/cancel";

            // 대상 서비스(영화 or 자전거) 컨트롤러가 데이터베이스를 이리저리 수정하기 위해 필요한 정보들을 바디 안에 꽉꽉 묶어줌
            Map<String, Object> data = new HashMap<>();
            data.put("transactionNum", transactionNum); // 거래번호
            data.put("memberCode", transaction.getMemberCode()); // 누가 취소했는지
            data.put("amountUsed", transaction.getAmountUsed()); // 돈 얼마 돌려줘야하는지 등등
            data.put("startDate", transaction.getStartDate());
            data.put("endDate", transaction.getEndDate());

            // 게이트웨이가 좋아하는 포맷으로 전체 바디 랩핑
            Map<String, Object> cancelRequest = new HashMap<>();
            cancelRequest.put("intent", intent);
            cancelRequest.put("data", data);

            // 게이트웨이가 어떤 마이크로서비스로 보낼지 결정할 수 있게 헤더에 반드시 intent를 박아줘야 함
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("intent", intent);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(cancelRequest, headers);

            // 다른 서버의 응답을 끝까지 기다린 다음
            restTemplate.postForEntity(gatewayUrl, entity, String.class);

            // 진짜 성공 처리!
            result.put("success", true);
            result.put("message", "예약이 성공적으로 취소되었습니다.");
        } catch (Exception e) {
            // 다른 서버가 꺼져있거나, 타임아웃 났거나, 취소 로직에서 오류가 났을 때
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "취소 처리 중 오류가 발생했습니다: " + e.getMessage());
        }

        return result;
    }
}