package com.hulahoop.blueback.ai.model.service.payment;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final TossPaymentService tossPaymentService;

    public PaymentController(TossPaymentService tossPaymentService) {
        this.tossPaymentService = tossPaymentService;
    }

    /**
     * 프론트에서 orderId, amount, orderName 전달
     */
    @PostMapping("/create")
    public Map<String, Object> createPayment(@RequestBody Map<String, Object> body) {

        String orderId = body.get("orderId").toString();
        long amount = Long.parseLong(body.get("amount").toString());
        String orderName = body.get("orderName").toString();

        return tossPaymentService.createPayment(orderId, amount, orderName);
    }

    /**
     * Toss 결제 승인 처리
     */
    @PostMapping("/confirm")
    public Map<String, Object> confirmPayment(@RequestBody Map<String, Object> body) {

        String paymentKey = body.get("paymentKey").toString();
        String orderId = body.get("orderId").toString();
        long amount = Long.parseLong(body.get("amount").toString());

        return tossPaymentService.confirmPayment(paymentKey, orderId, amount);
    }
}
