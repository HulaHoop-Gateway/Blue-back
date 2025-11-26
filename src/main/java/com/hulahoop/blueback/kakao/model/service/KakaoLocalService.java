package com.hulahoop.blueback.kakao.model.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KakaoLocalService {

    @Value("${kakao.rest.api.key}")
    private String kakaoApiKeyRaw;

    private String kakaoApiKey;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        if (kakaoApiKeyRaw == null) {
            System.out.println("❌ Kakao API Key Not Found! (null)");
            kakaoApiKey = "";
        } else {
            kakaoApiKey = kakaoApiKeyRaw.trim();
        }
        // 보안상 키 전체 출력보다는 일부만 출력하거나 생략하는 것이 좋습니다.
        System.out.println("🔑 Kakao API Key Loaded (Length: " + kakaoApiKey.length() + ")");
    }

    // =========================================================
    // 0. 주소 전처리 및 키워드 추출
    // =========================================================
    private String normalizeAddress(String address) {
        if (address == null)
            return null;
        return address.trim().replaceAll("\\(.*?\\)", "").trim();
    }

    private String trimQueryLength(String q) {
        if (q == null)
            return null;
        return q.length() > 100 ? q.substring(0, 100) : q;
    }

    public String extractPlaceKeyword(String input) {
        if (input == null)
            return null;
        String regex = "(\\S+역)|(\\S+동)|(\\S+구)|(\\S+시)";
        Matcher m = Pattern.compile(regex).matcher(input);
        return m.find() ? m.group() : null;
    }

    // =========================================================
    // 1. 공통 API 호출 메서드 (수정됨: URI 객체 수신, 헤더 정리)
    // =========================================================
    private ResponseEntity<Map> callKakaoAPI(URI uri) {
        try {
            if (kakaoApiKey == null || kakaoApiKey.isBlank()) {
                System.out.println("❌ Kakao API Key is NULL or BLANK");
                return null;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);
            // ⚠️ 중요: Referer, Host 헤더 제거 (오류의 주원인)

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            System.out.println("🌐 Request URI → " + uri); // 인코딩된 최종 주소 확인

            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);

            // 응답 확인용 로그
            if (response.getBody() != null) {
                List<?> docs = (List<?>) response.getBody().get("documents");
                System.out.println("📄 결과 개수: " + (docs != null ? docs.size() : 0));
            }

            return response;

        } catch (Exception e) {
            System.out.println("❌ Kakao API Call Error → " + e.getMessage());
            return null;
        }
    }

    // =========================================================
    // 2. 주소 검색 API
    // =========================================================
    private double[] searchByAddressAPI(String input) {
        try {
            if (input == null || input.isBlank())
                return null;

            // UriComponentsBuilder가 인코딩을 자동으로 처리합니다.
            URI uri = UriComponentsBuilder
                    .fromUriString("https://dapi.kakao.com/v2/local/search/address.json")
                    .queryParam("query", trimQueryLength(input))
                    .encode(StandardCharsets.UTF_8) // UTF-8로 안전하게 인코딩
                    .build()
                    .toUri();

            ResponseEntity<Map> response = callKakaoAPI(uri);
            if (response == null || response.getBody() == null)
                return null;

            List<Map<String, Object>> docs = (List<Map<String, Object>>) response.getBody().get("documents");

            if (docs != null && !docs.isEmpty()) {
                Map<String, Object> doc = docs.get(0);
                return new double[] {
                        Double.parseDouble(doc.get("y").toString()),
                        Double.parseDouble(doc.get("x").toString())
                };
            }
        } catch (Exception e) {
            System.out.println("❌ 주소 API 파싱 오류 → " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    // 3. 키워드 검색 API
    // =========================================================
    private double[] searchByKeywordAPI(String input) {
        try {
            if (input == null || input.isBlank())
                return null;

            URI uri = UriComponentsBuilder
                    .fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                    .queryParam("query", trimQueryLength(input))
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();

            ResponseEntity<Map> response = callKakaoAPI(uri);
            if (response == null || response.getBody() == null)
                return null;

            List<Map<String, Object>> docs = (List<Map<String, Object>>) response.getBody().get("documents");

            if (docs != null && !docs.isEmpty()) {
                Map<String, Object> doc = docs.get(0);
                return new double[] {
                        Double.parseDouble(doc.get("y").toString()),
                        Double.parseDouble(doc.get("x").toString())
                };
            }
        } catch (Exception e) {
            System.out.println("❌ 키워드 API 파싱 오류 → " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    // 4. 역 검색 (지하철)
    // =========================================================
    private double[] searchStationAPI(String keyword) {
        try {
            if (keyword == null || keyword.isBlank())
                return null;

            URI uri = UriComponentsBuilder
                    .fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                    .queryParam("query", trimQueryLength(keyword))
                    .queryParam("category_group_code", "SW8") // 지하철역 코드
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();

            ResponseEntity<Map> response = callKakaoAPI(uri);
            if (response == null || response.getBody() == null)
                return null;

            List<Map<String, Object>> docs = (List<Map<String, Object>>) response.getBody().get("documents");

            if (docs != null && !docs.isEmpty()) {
                Map<String, Object> doc = docs.get(0);
                return new double[] {
                        Double.parseDouble(doc.get("y").toString()),
                        Double.parseDouble(doc.get("x").toString())
                };
            }
        } catch (Exception e) {
            System.out.println("❌ 역 검색 파싱 오류 → " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    // 5. 통합 좌표 검색 (Main Entry)
    // =========================================================
    public Map<String, Object> searchCoordinate(String input) {
        if (input == null || input.isBlank())
            return null;

        System.out.println("\n⭐ 좌표 검색 시작: " + input);

        String normalized = normalizeAddress(input);
        double[] coord;

        // 1) 주소 검색 시도
        coord = searchByAddressAPI(normalized);
        if (coord != null) {
            System.out.println("✅ 주소 검색 성공");
            return makeCoordMap(coord);
        }

        // 2) 키워드 검색 시도
        coord = searchByKeywordAPI(normalized);
        if (coord != null) {
            System.out.println("✅ 키워드 검색 성공");
            return makeCoordMap(coord);
        }

        // 3) '역'으로 끝난다면 지하철역 검색 시도
        if (normalized.endsWith("역")) {
            coord = searchStationAPI(normalized);
            if (coord != null) {
                System.out.println("✅ 지하철역 검색 성공");
                return makeCoordMap(coord);
            }
        }

        System.out.println("❌ 좌표 검색 최종 실패");
        return null;
    }

    private Map<String, Object> makeCoordMap(double[] c) {
        Map<String, Object> map = new HashMap<>();
        map.put("lat", c[0]);
        map.put("lng", c[1]);
        System.out.println("[KakaoLocalService] 📍 찾은 좌표 → 위도(lat): " + c[0] + ", 경도(lng): " + c[1]);
        return map;
    }

    // =========================================================
    // 6. 거리 계산 및 정렬
    // =========================================================
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // 지구 반지름 (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        return R * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    public List<Map<String, Object>> sortCinemasByDistance(
            Map<String, Object> basisCoord,
            List<Map<String, Object>> cinemas) {
        if (basisCoord == null || cinemas == null || cinemas.isEmpty()) {
            return cinemas;
        }

        double userLat = (double) basisCoord.get("lat");
        double userLng = (double) basisCoord.get("lng");

        for (Map<String, Object> cinema : cinemas) {
            String address = String.valueOf(cinema.get("address"));

            // 영화관 주소도 좌표로 변환 (캐싱 고려 권장)
            double[] cinemaCoord = Optional.ofNullable(searchByAddressAPI(address))
                    .orElseGet(() -> searchByKeywordAPI(address)); // 주소 실패시 키워드로 재시도

            if (cinemaCoord == null) {
                cinema.put("distance", 9999.0); // 못 찾으면 아주 먼 거리로 설정
                continue;
            }

            double dist = calculateDistance(userLat, userLng, cinemaCoord[0], cinemaCoord[1]);
            cinema.put("distance", Math.round(dist * 100) / 100.0); // 소수점 2자리 반올림

            // ✅ 좌표를 cinema 객체에 추가 (지도 표시용)
            cinema.put("latitude", cinemaCoord[0]);
            cinema.put("longitude", cinemaCoord[1]);
        }

        cinemas.sort(Comparator.comparingDouble(
                c -> Double.parseDouble(c.get("distance").toString())));

        return cinemas;
    }

    public List<Map<String, Object>> sortBikesByDistance(
            Map<String, Object> basisCoord,
            List<Map<String, Object>> bikes) {
        if (basisCoord == null || bikes == null || bikes.isEmpty()) {
            return bikes;
        }

        double userLat = (double) basisCoord.get("lat");
        double userLng = (double) basisCoord.get("lng");

        for (Map<String, Object> bike : bikes) {
            // 자전거는 이미 latitude, longitude 필드를 가지고 있음
            Object latObj = bike.get("latitude");
            Object lngObj = bike.get("longitude");

            if (latObj == null || lngObj == null) {
                bike.put("distance", 9999.0);
                continue;
            }

            double bikeLat = (latObj instanceof Number) ? ((Number) latObj).doubleValue()
                    : Double.parseDouble(latObj.toString());
            double bikeLng = (lngObj instanceof Number) ? ((Number) lngObj).doubleValue()
                    : Double.parseDouble(lngObj.toString());

            double dist = calculateDistance(userLat, userLng, bikeLat, bikeLng);
            bike.put("distance", Math.round(dist * 100) / 100.0);
        }

        bikes.sort(Comparator.comparingDouble(
                b -> Double.parseDouble(b.get("distance").toString())));

        return bikes;
    }
}