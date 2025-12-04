package com.hulahoop.blueback.common.scheduler;

import com.hulahoop.blueback.history.model.dao.HistoryMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class TransactionStatusScheduler {

    private final HistoryMapper historyMapper;

    public TransactionStatusScheduler(HistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    @Scheduled(fixedRate = 60000) // 1분마다 실행
    @Transactional
    public void updatePendingTransactions() {
        System.out.println("Scheduler running: Checking recent transactions...");

        List<com.hulahoop.blueback.history.model.dto.HistoryResponseDto> recentList = historyMapper
                .findRecentTransactions();
        for (com.hulahoop.blueback.history.model.dto.HistoryResponseDto dto : recentList) {
            System.out.println("Tx: " + dto.getTransactionNum() + ", Status: '" + dto.getStatus() + "', StartDate: "
                    + dto.getStartDate());
        }

        int updatedCount = historyMapper.updatePendingToSuccess();
        System.out.println("Scheduler finished: Updated " + updatedCount + " transactions.");
    }
}
