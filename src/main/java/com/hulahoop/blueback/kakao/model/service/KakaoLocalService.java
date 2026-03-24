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

// 카카오 로컬 API(지도 검색)와 통신해서 사용자 주소나 키워드 기반으로 위도/경도 좌표를 따오고,
// 그 좌표를 바탕으로 가장 가까운 영화관이나 자전거 대여소를 거리순으로 정렬해주는 핵심 서비스
@Service
public class KakaoLocalService {

    // 카카오 디벨로퍼스에서 발급받은 REST API 키 (유출되면 남이 막 써서 요금 폭탄 맞으므로 yml에 숨김)
    @Value("${kakao.rest.api.key}")
    private String kakaoApiKeyRaw;

    private String kakaoApiKey;
    private final RestTemplate restTemplate = new RestTemplate();

    // Spring Bean이 처음 뜰 때(@PostConstruct) 키값이 제대로 로드되었는지 검사하는 초기화 메서드
    @PostConstruct
    public void init() {
        if (kakaoApiKeyRaw == null) {
            System.out.println("카카오 API 키를 찾을 수 없습니다 (null)");
            kakaoApiKey = "";
        } else {
            kakaoApiKey = kakaoApiKeyRaw.trim();
        }
        System.out.println("카카오 API 키 로드 완료 (길이: " + kakaoApiKey.length() + ")");
    }

    // =========================================================
    // 0. 주소 전처리 및 키워드 추출
    // =========================================================

    // DB에 보면 주소가 "서울특별시 강남구 역삼동 (무슨무슨아파트 어쩌구)" 이렇게 괄호가 포함된 경우가 많은데,
    // 이걸 카카오 API에 그대로 던지면 못 알아먹고 에러를 뱉는 경우가 잦음.
    // 그래서 괄호 정규식 "\\(.*?\\)" 을 써서 괄호 안의 내용을 아예 날려버리고 알맹이만 남김.
    private String normalizeAddress(String address) {
        if (address == null)
            return null;
        return address.trim().replaceAll("\\(.*?\\)", "").trim();
    }

    // 보안 및 방어코드: 누가 악의적으로 검색어를 엄청 길게 보내면 서버 터질 수 있으니 100자로 잘라버림
    private String trimQueryLength(String q) {
        if (q == null)
            return null;
        return q.length() > 100 ? q.substring(0, 100) : q;
    }

    // 챗봇 대화 중에 "강남역 자전거 찾아줘" 이랬을 때 카카오맵 키워드 검색을 위해
    // 문장에서 ~역, ~동, ~구, ~시 로 끝나는 단어만 쏙 뽑아내는 꼼수 로직
    public String extractPlaceKeyword(String input) {
        if (input == null)
            return null;
        String regex = "(\\S+역)|(\\S+동)|(\\S+구)|(\\S+시)";
        Matcher m = Pattern.compile(regex).matcher(input);
        return m.find() ? m.group() : null;
    }

    // =========================================================
    // 1. 공통 API 호출 헬퍼 메서드
    // =========================================================
    private ResponseEntity<Map> callKakaoAPI(URI uri) {
        try {
            if (kakaoApiKey == null || kakaoApiKey.isBlank()) {
                System.out.println("에러: 카카오 API 키가 비어있습니다");
                return null;
            }

            // 카카오 문서 규칙: 헤더에 "Authorization: KakaoAK {REST_API_KEY}" 형태로 박아줘야 인증 통과됨
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);

            // 한 가지 주의할 점: Spring WebClient나 브라우저는 자동으로 Host 등 헤더를 넣는데,
            // 종종 카카오 서버가 예상치 못한 Referer 헤더 때문에 인증 우회 시도로 착각하고 끊어버릴 때가 있음
            // 그래서 HttpEntity에 딱 필요한 Authorization만 넣어서 쏨

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            System.out.println("요청 출발 -> URI: " + uri);

            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null) {
                List<?> docs = (List<?>) response.getBody().get("documents");
                System.out.println("응답 받음 -> 파싱된 장소 개수: " + (docs != null ? docs.size() : 0));
            }

            return response;

        } catch (Exception e) {
            System.out.println("카카오 API 호출 중 에러 발생: " + e.getMessage());
            return null;
        }
    }

    // =========================================================
    // 2. 주소 검색 API (정확한 주소 텍스트를 위/경도로 바꿈)
    // =========================================================
    private double[] searchByAddressAPI(String input) {
        try {
            if (input == null || input.isBlank())
                return null;

            // 한글 URL 깨짐 방지를 위해 UriComponentsBuilder를 씀
            // 내부적으로 UTF-8 인코딩 처리를 대신 해줘서 '강남구' 같은 한글 파라미터를 %EAX%.. 형태로 변환해줌
            URI uri = UriComponentsBuilder
                    .fromUriString("https://dapi.kakao.com/v2/local/search/address.json")
                    .queryParam("query", trimQueryLength(input))
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();

            ResponseEntity<Map> response = callKakaoAPI(uri);
            if (response == null || response.getBody() == null)
                return null;

            // 카카오 응답 데이터 중 'documents' 리스트의 첫 번째 결과물의 x(경도), y(위도) 좌표를 가져옴
            List<Map<String, Object>> docs = (List<Map<String, Object>>) response.getBody().get("documents");

            if (docs != null && !docs.isEmpty()) {
                Map<String, Object> doc = docs.get(0);
                return new double[] {
                        Double.parseDouble(doc.get("y").toString()), // 위도 (Latitude)
                        Double.parseDouble(doc.get("x").toString()) // 경도 (Longitude)
                };
            }
        } catch (Exception e) {
            System.out.println("주소 API 파싱 중 에러 발생: " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    // 3. 키워드 검색 API ("CGV 강남" 처럼 상호명이나 대충 부르는 이름으로 찾을 때)
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
            System.out.println("키워드 API 파싱 중 에러 발생: " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    // 4. 역 검색 (지하철 카테고리 필터링)
    // =========================================================
    private double[] searchStationAPI(String keyword) {
        try {
            if (keyword == null || keyword.isBlank())
                return null;

            URI uri = UriComponentsBuilder
                    .fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                    .queryParam("query", trimQueryLength(keyword))
                    // 카테고리 그룹 코드를 전송해서 "지하철역(SW8)" 관련 결과만 제한적으로 걸러냄 -> 엉뚱한 밥집 역전우동 안나오게 방지
                    .queryParam("category_group_code", "SW8")
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
            System.out.println("역 검색 파싱 중 에러 발생: " + e.getMessage());
        }
        return null;
    }

    // =========================================================
    // 5. 통합 좌표 검색 메인 입구 (Fallback 구조)
    // =========================================================
    // 사용자가 입력한 단어가 주소일수도, 장소명일수도, 지하철역명일수도 있기 때문에,
    // 확률이 높은 것부터 차례대로 찍어보면서 하나라도 성공하면 바로 좌표를 리턴하는 3단계 필터링 구조임
    public Map<String, Object> searchCoordinate(String input) {
        if (input == null || input.isBlank())
            return null;

        System.out.println("\n좌표 통합 검색을 시작합니다. 사용자 입력어: " + input);

        String normalized = normalizeAddress(input);
        double[] coord;

        // 1) 주소 검색으로 먼저 찔러봄 (성공률이 제일 높음)
        coord = searchByAddressAPI(normalized);
        if (coord != null) {
            System.out.println("1단계: 주소 검색 방식으로 가져오는 데 성공했습니다");
            return makeCoordMap(coord);
        }

        // 2) 주소가 아니라 식당명 같은 장소 이름인 경우 키워드 장소 검색으로 시도
        coord = searchByKeywordAPI(normalized);
        if (coord != null) {
            System.out.println("2단계: 키워드 장소 검색 방식으로 가져오는 데 성공했습니다");
            return makeCoordMap(coord);
        }

        // 3) 혹시 끝 단위가 '역'으로 끝났다면, 지하철역 DB 전용으로 한번 더 찔러봄
        if (normalized.endsWith("역")) {
            coord = searchStationAPI(normalized);
            if (coord != null) {
                System.out.println("3단계: 지하철 전용 카테고리 검색으로 가져오는 데 성공했습니다");
                return makeCoordMap(coord);
            }
        }

        System.out.println("위 3가지 방식(주소, 키워드, 역명)을 모두 돌려봤지만 좌표를 찾지 못했습니다");
        return null;
    }

    private Map<String, Object> makeCoordMap(double[] c) {
        Map<String, Object> map = new HashMap<>();
        map.put("lat", c[0]); // 위도
        map.put("lng", c[1]); // 경도
        System.out.println("[KakaoLocalService] 좌표 바인딩 완료 → 위도: " + c[0] + ", 경도: " + c[1]);
        return map;
    }

    // =========================================================
    // 6. 거리 계산 (하버사인 공식 - Haversine Formula) 및 추천 정렬
    // =========================================================

    // 두 위/경도 좌표 간의 직선 거리를 구하는 구면 삼각법 수학 공식
    // 지구가 둥글기 때문에 평면 방정식이 아니라 삼각함수(sin, cos)를 써서 곡면 거리(호의 길이)를 구함
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // 지구 평균 반지름 (단위: 킬로미터)
        double dLat = Math.toRadians(lat2 - lat1); // 위도 차이를 라디안 값으로 변환
        double dLon = Math.toRadians(lon2 - lon1); // 경도 차이를 라디안 값으로 변환

        // 하버사인 중심식: 삼각함수 파티...
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);

        // 최종적으로 지구 반지름에 각도를 곱해 실제 킬로미터(km) 거리를 뽑아냄
        return R * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    // 내 현재 위치 좌표를 기준으로 전국의 수많은 영화관을 "가까운 순"으로 줄세워주는 메서드
    public List<Map<String, Object>> sortCinemasByDistance(
            Map<String, Object> basisCoord,
            List<Map<String, Object>> cinemas) {

        // 내 좌표나 영화관 목록이 안 들어왔으면 검사할 수 없으니 그대로 리턴
        if (basisCoord == null || cinemas == null || cinemas.isEmpty()) {
            return cinemas;
        }

        double userLat = (double) basisCoord.get("lat");
        double userLng = (double) basisCoord.get("lng");

        for (Map<String, Object> cinema : cinemas) {
            String address = String.valueOf(cinema.get("address"));

            // 영화관 DB에는 주소(글자)만 저장되어 있으므로 얘네들도 카카오한테 물어봐서 좌표로 일단 변환함
            // (주의: 매번 쏠 때마다 로딩시간이 걸리므로, 실무에선 이거 DB에 캐싱시켜놔야 함)
            double[] cinemaCoord = Optional.ofNullable(searchByAddressAPI(address))
                    .orElseGet(() -> searchByKeywordAPI(address));

            // 카카오 지도에도 등록 안된 유령 영화관이면 그냥 거리 9999km 때려서 목록 맨 뒤로 꼴아박게 만듦
            if (cinemaCoord == null) {
                cinema.put("distance", 9999.0);
                continue;
            }

            // 구면 거리 측정 공식 장동
            double dist = calculateDistance(userLat, userLng, cinemaCoord[0], cinemaCoord[1]);
            cinema.put("distance", Math.round(dist * 100) / 100.0); // 보기 좋게 소수점 2자리까지만 반올림 (예: 3.14km)

            // 나중에 프론트엔드가 지도 그릴 때 써먹을 수 있게 내친김에 딴 좌표까지 같이 객체에 구겨넣어줌
            cinema.put("latitude", cinemaCoord[0]);
            cinema.put("longitude", cinemaCoord[1]);
        }

        // List 안의 객체들이 들고 있는 'distance' 숫자를 비교해서 오름차순(가까운 게 1등) 정렬수행
        cinemas.sort(Comparator.comparingDouble(
                c -> Double.parseDouble(c.get("distance").toString())));

        return cinemas; // 정렬 싹 끝난 목록 리턴!
    }

    // 위 로직과 동일하지만 대상이 자전거 대여소인 경우
    public List<Map<String, Object>> sortBikesByDistance(
            Map<String, Object> basisCoord,
            List<Map<String, Object>> bikes) {

        if (basisCoord == null || bikes == null || bikes.isEmpty()) {
            return bikes;
        }

        double userLat = (double) basisCoord.get("lat");
        double userLng = (double) basisCoord.get("lng");

        for (Map<String, Object> bike : bikes) {
            // 특이사항: 영화관과 달리, 자전거 공공데이터는 이미 위/경도를 DB에 바로 들고 있음
            // 그래서 카카오 API를 매번 쏠 필요 없이 그냥 DB 값 꺼내서 수식 돌리면 됨 (개꿀)
            Object latObj = bike.get("latitude");
            Object lngObj = bike.get("longitude");

            if (latObj == null || lngObj == null) {
                bike.put("distance", 9999.0);
                continue;
            }

            // DB에서 꺼낸게 Number타입인지 String인지 헷갈릴 때를 대비한 안전 장치 캐스팅
            double bikeLat = (latObj instanceof Number) ? ((Number) latObj).doubleValue()
                    : Double.parseDouble(latObj.toString());
            double bikeLng = (lngObj instanceof Number) ? ((Number) lngObj).doubleValue()
                    : Double.parseDouble(lngObj.toString());

            double dist = calculateDistance(userLat, userLng, bikeLat, bikeLng);
            bike.put("distance", Math.round(dist * 100) / 100.0);
        }

        // 거리 기준 오름차순 정렬
        bikes.sort(Comparator.comparingDouble(
                b -> Double.parseDouble(b.get("distance").toString())));

        return bikes;
    }
}