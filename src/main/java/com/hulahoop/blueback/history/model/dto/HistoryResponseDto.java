package com.hulahoop.blueback.history.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class HistoryResponseDto {

    private Long transactionNum;
    private String memberName;
    private String memberCode;
    private String merchantName;
    private String merchantCode;
    private BigDecimal amountUsed;
    private LocalDate paymentDate;
    private String status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public HistoryResponseDto() {
    }

    public HistoryResponseDto(
            Long transactionNum,
            String memberName,
            String merchantName,
            BigDecimal amountUsed,
            LocalDate paymentDate,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate) {
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

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getMemberCode() {
        return memberCode;
    }

    public void setMemberCode(String memberCode) {
        this.memberCode = memberCode;
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

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    @Override
    public String toString() {
        return "HistoryResponseDto{" +
                "transactionNum=" + transactionNum +
                ", memberName='" + memberName + '\'' +
                ", memberCode='" + memberCode + '\'' +
                ", merchantName='" + merchantName + '\'' +
                ", merchantCode='" + merchantCode + '\'' +
                ", amountUsed=" + amountUsed +
                ", paymentDate=" + paymentDate +
                ", status='" + status + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
