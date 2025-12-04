package com.hulahoop.blueback.history.model.dao;

import com.hulahoop.blueback.history.model.dto.HistoryResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HistoryMapper {
    List<HistoryResponseDto> findHistoryByMemberCode(@Param("memberCode") String memberCode,
            @Param("status") String status);

    HistoryResponseDto findTransactionByNum(@Param("transactionNum") Long transactionNum);

    int updatePendingToSuccess();

    List<HistoryResponseDto> findRecentTransactions();
}