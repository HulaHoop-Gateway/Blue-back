package com.hulahoop.blueback.ai.model.service.session;

import java.util.*;

public class UserSession {

    // ✅ 단계 정의 업데이트 (BIKE_PAYMENT_CONFIRM, MOVIE_PAYMENT_CONFIRM 단계 추가)
    public enum Step {
        IDLE,
        BRANCH_SELECT,
        MOVIE_SELECT,
        SEAT_SELECT,
        MOVIE_PAYMENT_CONFIRM, // ✅ 영화 예약 결제 대기 단계 추가
        BIKE_SELECT,
        BIKE_TIME_INPUT,
        BIKE_PAYMENT_CONFIRM // ✅ 자전거 예약 결제 버튼 노출 및 확인 대기 단계
    }

    // 플로우 타입 구분
    public enum FlowType {
        NONE, MOVIE, BIKE
    }

    private Step step = Step.IDLE;
    private FlowType flowType = FlowType.NONE; // ✅ FlowType은 유지

    private final List<Map<String, Object>> history = new ArrayList<>();
    private final Map<String, Object> bookingContext = new HashMap<>();
    private List<Map<String, Object>> lastCinemas = new ArrayList<>();
    private List<Map<String, Object>> lastMovies = new ArrayList<>();
    private List<Map<String, Object>> lastSeats = new ArrayList<>();
    private List<Map<String, Object>> lastBikes = new ArrayList<>();

    // Getter / Setter
    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public FlowType getFlowType() {
        return flowType;
    }

    public void setFlowType(FlowType flowType) {
        this.flowType = flowType;
    }

    public List<Map<String, Object>> getHistory() {
        return history;
    }

    public Map<String, Object> getBookingContext() {
        return bookingContext;
    }

    public List<Map<String, Object>> getLastCinemas() {
        return lastCinemas;
    }

    public void setLastCinemas(List<Map<String, Object>> lastCinemas) {
        this.lastCinemas = lastCinemas;
    }

    public List<Map<String, Object>> getLastMovies() {
        return lastMovies;
    }

    public void setLastMovies(List<Map<String, Object>> lastMovies) {
        this.lastMovies = lastMovies;
    }

    public List<Map<String, Object>> getLastSeats() {
        return lastSeats;
    }

    public void setLastSeats(List<Map<String, Object>> lastSeats) {
        this.lastSeats = lastSeats;
    }

    public List<Map<String, Object>> getLastBikes() {
        return lastBikes;
    }

    public void setLastBikes(List<Map<String, Object>> lastBikes) {
        this.lastBikes = lastBikes;
    }

    // 기록 추가
    public void addHistory(Map<String, Object> entry) {
        history.add(entry);
    }

    // ✅ 세션 초기화 (FlowType도 초기화)
    public void reset() {
        step = Step.IDLE;
        flowType = FlowType.NONE; // ✅ FlowType도 초기 상태로 초기화해야 합니다.
        history.clear();
        bookingContext.clear();
        lastCinemas.clear();
        lastMovies.clear();
        lastSeats.clear();
        lastBikes.clear();
    }
}