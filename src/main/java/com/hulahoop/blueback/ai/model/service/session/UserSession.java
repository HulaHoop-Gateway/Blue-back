package com.hulahoop.blueback.ai.model.service.session;

import java.util.*;

public class UserSession {
    public enum Step { IDLE, BRANCH_SELECT, MOVIE_SELECT, SEAT_SELECT, BIKE_SELECT }

    private Step step = Step.IDLE;
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final Map<String, Object> bookingContext = new HashMap<>();
    private List<Map<String, Object>> lastCinemas = new ArrayList<>();
    private List<Map<String, Object>> lastMovies = new ArrayList<>();
    private List<Map<String, Object>> lastSeats = new ArrayList<>();

    // ───────── Getter / Setter ─────────
    public Step getStep() { return step; }
    public void setStep(Step step) { this.step = step; }

    public List<Map<String, Object>> getHistory() { return history; }
    public Map<String, Object> getBookingContext() { return bookingContext; }

    public List<Map<String, Object>> getLastCinemas() { return lastCinemas; }
    public void setLastCinemas(List<Map<String, Object>> lastCinemas) { this.lastCinemas = lastCinemas; }

    public List<Map<String, Object>> getLastMovies() { return lastMovies; }
    public void setLastMovies(List<Map<String, Object>> lastMovies) { this.lastMovies = lastMovies; }

    public List<Map<String, Object>> getLastSeats() { return lastSeats; }
    public void setLastSeats(List<Map<String, Object>> lastSeats) { this.lastSeats = lastSeats; }

    // ───────── 관리 메서드 ─────────
    public void addHistory(Map<String, Object> entry) {
        history.add(entry);
    }

    public void reset() {
        step = Step.IDLE;
        history.clear();
        bookingContext.clear();
        lastCinemas.clear();
        lastMovies.clear();
        lastSeats.clear();
    }
}