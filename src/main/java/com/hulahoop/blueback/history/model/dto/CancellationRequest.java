package com.hulahoop.blueback.history.model.dto;

public class CancellationRequest {
    private Long transactionNum;

    public CancellationRequest() {
    }

    public CancellationRequest(Long transactionNum) {
        this.transactionNum = transactionNum;
    }

    public Long getTransactionNum() {
        return transactionNum;
    }

    public void setTransactionNum(Long transactionNum) {
        this.transactionNum = transactionNum;
    }
}
