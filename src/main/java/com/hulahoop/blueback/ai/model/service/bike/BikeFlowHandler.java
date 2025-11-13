package com.hulahoop.blueback.ai.model.service.bike;

import com.hulahoop.blueback.ai.model.dto.BikeDTO;
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

    public List<BikeDTO> handleBikeFlow(String userInput, UserSession session) {
        if (session.getStep() == UserSession.Step.IDLE && userInput.contains("자전거")) {
            Map<String, Object> r = intentService.processIntent("bike_list", Map.of());
            List<Map<String, Object>> bikes = safeList(r.get("bicycles"));

            if (bikes.isEmpty()) {
                return new ArrayList<>(); // Return empty list if no bikes
            }

            List<BikeDTO> bikeDTOs = new ArrayList<>();
            for (Map<String, Object> b : bikes) {
                String bicycleCode = String.valueOf(b.get("bicycleCode"));
                String bicycleType = (String) b.get("bicycleType");
                String status = (String) b.get("status");
                double latitude = ((Number) b.get("latitude")).doubleValue();
                double longitude = ((Number) b.get("longitude")).doubleValue();
                bikeDTOs.add(new BikeDTO(bicycleCode, bicycleType, status, latitude, longitude));
            }
            return bikeDTOs;
        }
        return null; // Or throw an exception, depending on desired behavior for non-bike input
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : new ArrayList<>();
    }
}