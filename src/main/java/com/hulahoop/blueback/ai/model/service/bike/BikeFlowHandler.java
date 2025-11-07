package com.hulahoop.blueback.ai.model.service.bike;

import com.hulahoop.blueback.ai.model.service.IntentService;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BikeFlowHandler {

    private final IntentService intentService;

    public BikeFlowHandler(IntentService intentService) {
        this.intentService = intentService;
    }

    public String handleBikeFlow(String userInput, UserSession session) {
        if (session.getStep() == UserSession.Step.IDLE && userInput.contains("자전거")) {
            Map<String, Object> r = intentService.processIntent("bike_list", Map.of());
            List<Map<String, Object>> bikes = safeList(r.get("bicycles"));

            if (bikes.isEmpty()) return "🚲 대여 가능한 자전거가 없습니다.";

            StringBuilder sb = new StringBuilder("[대여 가능 자전거]\n\n");
            int i = 1;
            for (Map<String, Object> b : bikes) {
                sb.append(i++).append(". 번호: ").append(b.get("bicycleCode"))
                        .append(" | 종류: ").append(b.get("bicycleType"))
                        .append(" | 상태: ").append(b.get("status"))
                        .append(" | 위치: ").append(b.get("latitude")).append(", ").append(b.get("longitude")).append("\n");
            }
            return sb.toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : new ArrayList<>();
    }
}