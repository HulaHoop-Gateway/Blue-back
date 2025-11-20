package com.hulahoop.blueback.history.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class HistoryResponseDto {

    private Long transactionNum;
    private String memberName;
    private String merchantName;
    private BigDecimal amountUsed;
    private LocalDate paymentDate;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;

    public HistoryResponseDto() {
    }

    public HistoryResponseDto(
            Long transactionNum,
            String memberName,
            String merchantName,
            BigDecimal amountUsed,
            LocalDate paymentDate,
            String status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.transactionNum = transactionNum;
        this.memberName = memberName;
        this.merchantName = merchantName;
        this.amountUsed = amountUsed;
        this.paymentDate = paymentDate;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getTransactionNum() {
        return transactionNum;
    }

    public void setTransactionNum(Long transactionNum) {
        this.transactionNum = transactionNum;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public BigDecimal getAmountUsed() {
        return amountUsed;
    }

    public void setAmountUsed(BigDecimal amountUsed) {
        this.amountUsed = amountUsed;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
