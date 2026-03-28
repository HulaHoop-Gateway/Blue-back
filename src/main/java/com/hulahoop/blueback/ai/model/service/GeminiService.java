package com.hulahoop.blueback.ai.model.service;

import com.hulahoop.blueback.ai.model.dto.AiResponseDTO;
import com.hulahoop.blueback.ai.model.service.bike.BikeFlowRouter;
import com.hulahoop.blueback.ai.model.service.movie.MovieFlowRouter;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 사용자가 입력한 모든 프롬프트를 전처리하고, 어떤 흐름(영화, 자전거, 일상대화)으로 빠질지 결정하는 가장 핵심적인 중추 클래스
@Service
public class GeminiService {

    // 외부 API(카카오, 제미나이 등)와 통신하기 위해 RestTemplate을 사용 (최근엔 WebClient를 권장하지만 동기 테스트엔
    // 편함)
    private final RestTemplate restTemplate = new RestTemplate();

    // 라우터: 여기가 영화 파트인지 자전거 파트인지 결판나면 각 라우터한테 하청을 줌
    private final MovieFlowRouter movieFlowRouter;
    private final BikeFlowRouter bikeFlowRouter;

    // 여기가 아주 중요! MSA 구조는 기본적으로 Stateless지만, 챗봇은 '이전 대화를 기억해야' 하므로 세션을 만들어둠
    // 서버 메모리에 Map 형태로 <회원아이디, 세션객체> 를 저장해둬서 사용자가 예약하다 중간에 끊기지 않게 함
    // (단, 이 방식은 서버가 재시작되면 날아가기 때문에 실무에선 Redis로 세션을 관리하는게 좋음)
    private final Map<String, UserSession> userSessions = new HashMap<>();

    // application.yml에 숨겨둔 구글 Gemini API 키 주입
    @Value("${gemini.api.key}")
    private String apiKey;

    // Gemini 2.5 flash 모델 버전 엔드포인트 URL
    private final String baseUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    public GeminiService(MovieFlowRouter movieFlowRouter,
            BikeFlowRouter bikeFlowRouter) {
        this.movieFlowRouter = movieFlowRouter;
        this.bikeFlowRouter = bikeFlowRouter;
    }

    // synchronized 키워드를 붙여서, 같은 유저가 동시에 여러번 따닥 눌렀을 때 세션 데이터가 엉키는 동시성 문제를 방지함
    public synchronized AiResponseDTO askGemini(String prompt, String userId) {

        // 로그인 안 한 사람이 여기까지 우회해서 들어왔다면 바로 커트시킴
        if (userId == null || userId.isBlank()) {
            return new AiResponseDTO("유효하지 않은 사용자입니다. 다시 로그인해주세요.");
        }

        // 유저 아이디로 세션을 찾고, 처음 말 거는 거라면 새 UserSession 객체를 하나 할당해줌
        userSessions.putIfAbsent(userId, new UserSession());
        UserSession session = userSessions.get(userId);

        // 구글 Gemini API가 요구하는 JSON 배열 구조에 맞게 <유저 입력값>을 히스토리에 누적시킴 (role: user)
        session.getHistory().add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt))));

        // 사용자가 "내일 영화" 처럼 말했을 때 텍스트에서 날짜를 슥 뽑아내서 세션 컨텍스트(데이터 저장소)에 보관함
        LocalDate parsedDate = extractDateFromText(prompt);
        session.getBookingContext().put("targetDate", parsedDate.toString());

        // 검색 비교하기 쉽게 무조건 소문자화, 공백제거
        String lower = prompt.toLowerCase().trim();

        // --- 여기서부터 라우팅 분기문 시작 ---

        // 1. 취소 흐름 최우선 방어
        // 만약 영화 취소 진행 중이었는데 다른 단어를 말해도 일단 취소 라우터가 먼저 인터셉트해서 처리함
        if (movieFlowRouter.isInCancelFlow(userId)) {
            String result = movieFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                response.setCinemas(session.getLastCinemas());
            }
            return response;
        }

        // 2. 이미 시작된 예약 흐름이 있을 때 (Step이 IDLE이 아닐 때)
        if (session.getStep() != UserSession.Step.IDLE) {

            // 흐름 타입(영화/자전거)이 날아갔는데 세션 데이터(마지막 본 목록 등)가 살아있다면 유추해서 복구시도
            if (session.getFlowType() == UserSession.FlowType.NONE) {
                if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                    session.setFlowType(UserSession.FlowType.MOVIE);
                } else if (session.getLastBikes() != null && !session.getLastBikes().isEmpty()) {
                    session.setFlowType(UserSession.FlowType.BIKE);
                }
            }

            // FlowType에 따라 하청업체(라우터)에 사용자 입력값을 토스
            if (session.getFlowType() == UserSession.FlowType.MOVIE) {
                String result = movieFlowRouter.handle(prompt, session, userId);
                AiResponseDTO response = new AiResponseDTO(result);
                // 응답에 영화관 목록이 세팅되어있으면 모달창 띄우라고 같이 내려줌
                if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                    response.setCinemas(session.getLastCinemas());
                }
                return response;
            }

            if (session.getFlowType() == UserSession.FlowType.BIKE) {
                String result = bikeFlowRouter.handle(prompt, session, userId);
                AiResponseDTO response = new AiResponseDTO(result);
                // 마찬가지로 자전거 목록 띄울 필요 있으면 내려줌
                if (session.getLastBikes() != null && !session.getLastBikes().isEmpty()) {
                    response.setBicycles(session.getLastBikes());
                }
                return response;
            }
        }

        // 3. 종료 의도 감지
        // 예약하다가 맘에 안 들어서 "취소" 라고 쳤으면 세션 깡통(초기화)으로 만들고 처음으로 돌아감
        if (isCancelIntent(prompt)) {
            session.reset();
            return new AiResponseDTO("대화를 종료했습니다. 필요하시면 다시 말씀해주세요.");
        }

        // 4. 명시적인 직접 호출 처리
        // 텍스트에 "영화 예약" 같이 콕 집어 말하면 쓸데없는 파싱 없이 바로 FlowType 고정하고 시작함
        if (lower.contains("영화 예약") || lower.contains("영화 예매")) {
            session.setFlowType(UserSession.FlowType.MOVIE);
            String result = movieFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                response.setCinemas(session.getLastCinemas());
            }
            return response;
        }

        if (lower.contains("자전거 예약") ||
                lower.contains("따릉이 예약") ||
                lower.contains("바이크 예약")) {
            session.setFlowType(UserSession.FlowType.BIKE);
            String result = bikeFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastBikes() != null && !session.getLastBikes().isEmpty()) {
                response.setBicycles(session.getLastBikes());
            }
            return response;
        }

        // 5. 너무 짧고 모호한 키워드 반사
        // "예약" 딱 두글자 쳤는데 지맘대로 영화를 예약해버리면 안 되니까, 되물어보는 안전장치
        if (lower.equals("예약")) {
            return new AiResponseDTO(
                    "어떤 예약을 도와드릴까요?\n\n" +
                            "영화 예매\n자전거 대여\n\n말씀해주세요!");
        }

        // 6. 유사 키워드 호출 감지
        // 위에서 안 걸러졌는데 문장에 "따릉이"가 껴있으면 자전거로 보내줌
        if (containsAny(lower, List.of("자전거", "따릉이", "바이크", "전기자전거"))) {
            session.setFlowType(UserSession.FlowType.BIKE);
            String result = bikeFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastBikes() != null && !session.getLastBikes().isEmpty()) {
                response.setBicycles(session.getLastBikes());
            }
            return response;
        }

        // 예약 취소 번호입력(10자리 숫자) 또는 영화 키워드 들어오면 영화로 보냄
        if (containsAny(lower, List.of("영화", "예매", "상영", "시간표"))
                || prompt.matches("^\\d{10}$")) {
            session.setFlowType(UserSession.FlowType.MOVIE);
            String result = movieFlowRouter.handle(prompt, session, userId);
            AiResponseDTO response = new AiResponseDTO(result);
            if (session.getLastCinemas() != null && !session.getLastCinemas().isEmpty()) {
                response.setCinemas(session.getLastCinemas());
            }
            return response;
        }

        // 7. 모든 분기(영화, 자전거, 취소 등)에 해당 안 되면?
        // 이제서야 마지막 보루인 진짜 Gemini LLM한테 질문을 넘겨서(Call) 일반 대화(Free Chat)처럼 답변을 쳐줌
        return callGeminiFreeChat(session.getHistory());
    }

    // 자연어 안에서 날짜("내일", "10월 5일" 등)를 파싱해내는 노가다성 헬퍼 메서드
    private LocalDate extractDateFromText(String text) {
        if (text == null)
            return LocalDate.now();

        text = text.toLowerCase().trim();
        LocalDate today = LocalDate.now();

        if (text.contains("내일"))
            return today.plusDays(1);
        if (text.contains("모레"))
            return today.plusDays(2);

        // 자바 정규표현식(Regex)을 이용해 문자열 안에서 숫자로 된 몇월 며칠을 귀신같이 뽑아냄
        Pattern p = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
        Matcher m = p.matcher(text);

        if (m.find()) {
            int month = Integer.parseInt(m.group(1)); // 첫번째 괄호가 월
            int day = Integer.parseInt(m.group(2)); // 두번째 괄호가 일
            return LocalDate.of(2025, month, day);
        }

        return today; // 못 찾으면 걍 오늘 날짜로 퉁침
    }

    // 예약 서비스 말고 평범한 스몰토크가 들어왔을 때, 진짜 Gemini API와 통신하는 메서드
    private AiResponseDTO callGeminiFreeChat(List<Map<String, Object>> history) {
        // history 안에는 내가 한 질문, 봇이 한 대답이 누적돼있어서 문맥이 파악 가능해짐
        Map<String, Object> req = Map.of("contents", history);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            // 구글에 HTTP POST 요청 날림
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "?key=" + apiKey,
                    new HttpEntity<>(req, headers),
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                return new AiResponseDTO("AI 서버 오류: " + response.getStatusCode());
            }

            // 구글에서 응답온 끔찍하게 깊은 JSON 구조 파싱하기 (candidates -> content -> parts -> text 추출)
            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> cand = (List<Map<String, Object>>) body.get("candidates");
            Map<String, Object> content = (Map<String, Object>) cand.get(0).get("content");
            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
            String text = parts.get(0).get("text");

            // AI가 대답한 것도 히스토리에 role: "model" 로 저장해놔야 다음 질문할 때 제미나이가 기억함
            history.add(Map.of("role", "model", "parts", List.of(Map.of("text", text))));
            return new AiResponseDTO(text);

        } catch (Exception e) {
            return new AiResponseDTO("현재 AI 응답이 원활하지 않습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    // 중간에 "취소해" 라는 문장이 다른 말에 포함되었는지(`contains`)가 아니라,
    // 딱 한 단어로 취소의도를 명시했는지 빡빡하게 검사하려고 `equals`를 쓴 종료 체크 헬퍼
    private boolean isCancelIntent(String text) {
        if (text == null)
            return false;
        String trimmed = text.trim();
        return trimmed.equals("그만") ||
                trimmed.equals("취소") ||
                trimmed.equals("끝") ||
                trimmed.equals("종료") ||
                trimmed.equals("나가기") ||
                trimmed.equals("끝내기") ||
                trimmed.equals("안할래");
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null)
            return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    public void resetConversation(String userId) {
        if (userId != null && userSessions.containsKey(userId)) {
            userSessions.get(userId).reset();
        }
    }
}
