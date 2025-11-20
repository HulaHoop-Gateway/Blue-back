package com.hulahoop.blueback.history.model.service;

import com.hulahoop.blueback.history.model.dao.HistoryMapper;
import com.hulahoop.blueback.history.model.dto.HistoryResponseDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    private final HistoryMapper historyMapper;

    public HistoryService(HistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    public List<HistoryResponseDto> getTransactionHistory(String memberCode, String status) {
        return historyMapper.findHistoryByMemberCode(memberCode, status);
    }
}