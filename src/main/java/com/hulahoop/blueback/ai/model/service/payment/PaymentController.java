package com.hulahoop.blueback.ai.model.service.payment;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

// 토스페이먼츠(결제 대행사) 연동을 위해 프론트엔드와 백엔드가 데이터를 주고받는 창구
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final TossPaymentService tossPaymentService;

    public PaymentController(TossPaymentService tossPaymentService) {
        this.tossPaymentService = tossPaymentService;
    }

    // [1단계] 결제 준비 단계
    // 프론트엔드에서 "나 12000원짜리 결제창 띄울래" 라고 하면 서버가 검증용 데이터를 세팅해주는 곳
    // 실제로는 여기서 DB에 주문 임시 정보(PENDING 상태)를 만들고 고유 orderId를 반환해주는 게 정석인데,
    // 현재는 구조를 타 서비스(MSA)에 맡기거나 테스트용으로 쓰느라 받은 값을 그대로 에코(메아리)쳐서 내려줌
    @PostMapping("/create")
    public Map<String, Object> createPayment(@RequestBody Map<String, Object> body) {

        String orderId = body.get("orderId").toString();
        long amount = Long.parseLong(body.get("amount").toString());
        String orderName = body.get("orderName").toString();

        return tossPaymentService.createPayment(orderId, amount, orderName);
    }

    // [2단계] 최종 결제 승인 요청 (가장 중요)
    // 브라우저에서 사용자가 카드사 앱 켜서 지문 인식하고 결제 성공 메시지까지 떴다 하더라도,
    // 아직 돈이 빠져나간 게 아님! (토스페이먼츠의 특징)
    //
    // 프론트가 받아온 암호화된 `paymentKey`를 끄집어내서 백엔드로 넘겨주면,
    // 백엔드가 자기만 아는 시크릿 키를 장착해서 토스 본사에 "야, 이거 결제 확정(짜를라니까 돈빼가라)해줘!" 라고 찌르는 역할
    @PostMapping("/confirm")
    public Map<String, Object> confirmPayment(@RequestBody Map<String, Object> body) {

        String paymentKey = body.get("paymentKey").toString(); // 토스가 준 영수증 키
        String orderId = body.get("orderId").toString(); // 아까 만든 주문 아이디
        long amount = Long.parseLong(body.get("amount").toString()); // 금액 (금액 위조 방지용 검증값)

        // 서비스한테 토스 서버에 승인 요청 날리라고 시킴
        return tossPaymentService.confirmPayment(paymentKey, orderId, amount);
    }
}
