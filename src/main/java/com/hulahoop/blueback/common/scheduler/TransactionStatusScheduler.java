package com.hulahoop.blueback.common.scheduler;

import com.hulahoop.blueback.history.model.dao.HistoryMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 우리가 잠든 사이(?) 백그라운드에서 주기적으로 돌아가면서 찌꺼기 데이터를 청소하거나 정리해주는 스프링 스케줄러 클래스
@Component
public class TransactionStatusScheduler {

    private final HistoryMapper historyMapper;

    public TransactionStatusScheduler(HistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    // @Scheduled 어노테이션을 쓰면 따로 쓰레드나 루프를 만들지 않아도 서버가 켜져있는 동안 이 메서드가 지정된 주기마다 무한 실행됨
    // 1분(60000ms)마다 주기적으로 데이터베이스를 체크해서 상태 업데이트를 시도함
    //
    // 왜 이런 로직이 필요한가?
    // 토스페이먼츠창을 띄워놓고 결제가 된 건지, 아니면 유저가 그냥 브라우저를 확 꺼버렸는지 백엔드는 즉각적으로 알 방법이 없음
    // 그래서 임시 상태(PENDING)로 박혀있는 결제 내역들이 쌓일 텐데,
    // 이런 찌꺼기들 중 성공해야 할 건 성공시키고, 죽은 건 걷어내는 등의 배치(Batch) 성격의 스캔 작업을 하는 거임
    @Scheduled(fixedRate = 60000)
    // 이 작업 하다가 DB가 꼬이지 않도록 묶어주는 트랜잭션 선언 보장
    @Transactional
    public void updatePendingTransactions() {
        System.out.println("스케줄러 실행: 최근 예약/결제 내역 확인 중...");

        // 가장 최근에 일어난 트랜잭션들을 DB에서 긁어옴 (Mapper 단에서 시간을 제한해야 풀 스캔 성능 저하가 안 생김)
        List<com.hulahoop.blueback.history.model.dto.HistoryResponseDto> recentList = historyMapper
                .findRecentTransactions();

        // 로깅용 루프
        for (com.hulahoop.blueback.history.model.dto.HistoryResponseDto dto : recentList) {
            System.out.println("예약번호: " + dto.getTransactionNum() + ", 상태: '" + dto.getStatus() + "', 시작일: "
                    + dto.getStartDate());
        }

        // 실제로 PENDING -> SUCCESS 등 필요한 비즈니스 로직에 맞춰서 UPDATE 쿼리 한방에 침 (벌크 연산)
        int updatedCount = historyMapper.updatePendingToSuccess();

        System.out.println("스케줄러 완료: " + updatedCount + "건의 내역 상태 업데이트 완료.");
    }
}
