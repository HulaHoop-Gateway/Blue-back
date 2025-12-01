package com.hulahoop.blueback.ai.model.service.movie;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MovieFormatter {

    public String formatCinemas(List<Map<String, Object>> cinemas) {
        if (cinemas == null || cinemas.isEmpty()) return "영화관 정보가 없습니다.";

        StringBuilder sb = new StringBuilder("📍 가까운 영화관 목록\n\n");
        int i = 1;
        for (Map<String, Object> cinema : cinemas) {

            double dist = cinema.get("distance") != null
                    ? Math.round(((double) cinema.get("distance")) * 10) / 10.0
                    : -1;

            sb.append(i++).append(") ")
                    .append(cinema.get("branch_name"))
                    .append(" - ").append(dist).append(" km\n")
                    .append("   📍 주소: ").append(cinema.get("address"))
                    .append(" (지점코드: ").append(cinema.get("branch_num")).append(")\n\n");
        }
        return sb.toString();
    }

    public String formatSchedules(List<Map<String, Object>> schedules) {
        if (schedules == null || schedules.isEmpty()) return "상영 스케줄이 없습니다.";

        StringBuilder sb = new StringBuilder("[상영 스케줄 목록]\n\n");
        int i = 1;
        for (Map<String, Object> schedule : schedules) {
            sb.append(i++).append(". ")
                    .append(schedule.get("movieTitle")).append("\n")
                    .append("   날짜: ").append(schedule.get("screeningDate")).append("\n")
                    .append("   상영관: ").append(schedule.get("screeningNumber")).append("관\n")
                    .append("   지점: ").append(schedule.get("branchName")).append("\n\n");
        }
        return sb.toString();
    }

    public String formatSeats(List<Map<String, Object>> seats) {
        if (seats == null || seats.isEmpty()) return "좌석 정보가 없습니다.";

        StringBuilder sb = new StringBuilder();
        Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
        Set<Integer> aisleCols = new TreeSet<>();

        for (Map<String, Object> seat : seats) {
            String row = String.valueOf(seat.get("row_label"));
            rows.computeIfAbsent(row, k -> new ArrayList<>()).add(seat);

            int isAisle = Integer.parseInt(String.valueOf(seat.get("is_aisle")));
            if (isAisle == 1) {
                aisleCols.add(Integer.parseInt(String.valueOf(seat.get("col_num"))));
            }
        }

        rows.values().forEach(rowSeats ->
                rowSeats.sort(Comparator.comparingInt(s -> Integer.parseInt(String.valueOf(s.get("col_num")))))
        );

        for (String row : rows.keySet()) {
            sb.append(row).append(" | ");
            for (Map<String, Object> seat : rows.get(row)) {
                int isAisle = Integer.parseInt(String.valueOf(seat.get("is_aisle")));
                boolean reserved = Boolean.parseBoolean(String.valueOf(seat.get("reserved")));

                if (isAisle == 1) {
                    sb.append("   ");
                } else {
                    sb.append(reserved ? "🟥" : "🟩").append(" ");
                }
            }
            sb.append("\n");
        }

        sb.append("\n🟩 가능 / 🟥 예약됨\n");
        if (!aisleCols.isEmpty()) {
            sb.append("*").append(String.join(",", aisleCols.stream().map(String::valueOf).toList()))
                    .append("열은 통로입니다.\n");
        }
        return sb.toString();
    }

    public String formatReservations(List<Map<String, Object>> reservations) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reservations.size(); i++) {
            Map<String, Object> r = reservations.get(i);
            sb.append("🔹 ").append(i + 1).append("번 예매\n")
                    .append("🎬 영화: ").append(r.get("movieTitle")).append("\n")
                    .append("🏢 지점: ").append(r.get("branchName")).append("\n")
                    .append("📅 상영일시: ").append(r.get("screeningDate")).append("\n")
                    .append("💺 좌석: ").append(r.get("seatLabel")).append("\n\n");
        }
        return sb.toString();
    }
}
