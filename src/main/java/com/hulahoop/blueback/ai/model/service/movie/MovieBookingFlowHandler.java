package com.hulahoop.blueback.ai.model.service.movie;

import com.hulahoop.blueback.ai.model.service.IntentService;
import com.hulahoop.blueback.ai.model.service.session.UserSession;
import com.hulahoop.blueback.email.model.service.EmailService;
import com.hulahoop.blueback.kakao.model.service.KakaoLocalService;
import com.hulahoop.blueback.member.model.dao.UserMapper;
import com.hulahoop.blueback.member.model.dto.MemberDTO;
import org.springframework.stereotype.Component;

import java.util.*;

//  영화 예약 5단계 플로우를 총괄하는 지휘통제실 
// 제미나이가 "이거 영화 예약이네!" 하고 여기로 넘기면, 이 클래스가 사용자의 진행 상태(Step)를 보면서 순서대로 이끌어줌
@Component
public class MovieBookingFlowHandler {

    private final IntentService intentService; // 게이트웨이랑 통신할 때 쓰는 도구
    private final MovieFormatter formatter; // 딱딱한 DB 데이터를 예쁜 채팅 텍스트로 꾸며주는 도우미
    private final UserMapper userMapper; // 본진(Blue) DB에서 회원 주소나 번호 꺼낼 때 씀
    private final KakaoLocalService kakaoLocalService; // 카카오맵 API 붙여서 가장 가까운 영화관 찾아주는 녀석
    private final EmailService emailService; // 예매 성공시 메일 쏴주는 서비스

    public MovieBookingFlowHandler(
            IntentService intentService,
            MovieFormatter formatter,
            UserMapper userMapper,
            KakaoLocalService kakaoLocalService,
            EmailService emailService) {
        this.intentService = intentService;
        this.formatter = formatter;
        this.userMapper = userMapper;
        this.kakaoLocalService = kakaoLocalService;
        this.emailService = emailService;
    }

    // 제네릭 캐스팅 경고 없애주는 유틸
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object obj) {
        return (obj instanceof List) ? (List<Map<String, Object>>) obj : new ArrayList<>();
    }

    // 입력어 중에 내가 찾는 키워드가 하나라도 있는지 뒤져보는 유틸
    private boolean containsAny(String text, List<String> words) {
        if (text == null)
            return false;
        String lower = text.toLowerCase();
        return words.stream().anyMatch(lower::contains);
    }

    // 사용자가 "음.. 1번이요" 라고 쳤을 때 여기서 숫자 '1'만 쏙 빼내는 기능
    // 크기가 3개인데 실수로 5번을 고르거나 하면 null 뱉어서 다시 고르라고 빠꾸먹임
    private Integer resolveIndexFromInput(String input, int maxSize) {
        if (input == null)
            return null;
        String digits = input.replaceAll("[^0-9]", ""); // 정규식: 숫자 빼고 다 날려버림
        if (digits.isEmpty())
            return null;
        int v = Integer.parseInt(digits);
        return (v >= 1 && v <= maxSize) ? v : null;
    }

    // "A3", "c12" 등 문자+숫자 조합 좌석표를 행/열 분리해서 실제 좌석 코드랑 매칭해주는 기능
    private Map<String, Object> findSeatByLabel(List<Map<String, Object>> seats, String label) {
        if (label.length() < 2)
            return null; // A 처럼 한글자면 무조건 에러
        String row = label.substring(0, 1); // 첫 글자 뽑기 (A)
        String col = label.substring(1); // 나머지 뽑기 (3)

        // 영화 서버에서 가져온 전체 좌석표를 돌면서 일치하는 놈 검색
        for (Map<String, Object> seat : seats) {
            if (row.equalsIgnoreCase(String.valueOf(seat.get("row_label"))) &&
                    col.equals(String.valueOf(seat.get("col_num")))) {
                return seat;
            }
        }
        return null; // 그런 자리 없으면 컷
    }

    // 어느 단계에 있든 언제나 "취소해", "다른거 할래" 라고 말하면 즉시 흐름 깨고 나가는 탈출구
    private String checkGlobalCommands(String userInput, UserSession s) {
        String lower = userInput.toLowerCase();

        if (containsAny(lower, List.of("그만", "종료", "취소", "나가기", "닫기", "안할래"))) {
            s.reset();
            return "네, 알겠습니다. 필요하실 때 언제든 불러주세요.";
        }

        if (containsAny(lower, List.of("조회", "예매함", "예약함", "내역"))) {
            s.reset();
            return "예매 내역 조회 기능으로 이동합니다. 무엇을 조회할까요?";
        }

        if (containsAny(lower, List.of("자전거", "바이크"))) {
            s.reset();
            return "자전거 예약 기능으로 이동합니다. 무엇을 도와드릴까요?";
        }

        return null;
    }

    // 문장에 "오늘", "내일", "11월 4일" 같은 날짜 키워드가 숨어있으면 찾아내는 녀석
    private String extractDateFilter(String userInput) {
        String lower = userInput.toLowerCase();

        if (lower.contains("오늘"))
            return "today";
        if (lower.contains("내일"))
            return "tomorrow";

        if (lower.matches(".*\\d{1,2}월\\s*\\d{1,2}일.*")) {
            String month = lower.replaceAll(".*?(\\d{1,2})월.*", "$1");
            String day = lower.replaceAll(".*?(\\d{1,2})일.*", "$1");
            return "2025-" + month + "-" + day; // 연도는 하드코딩 되어있음(추후 개선 대상)
        }

        return null;
    }

    // 여기가 메인 진입점. GeminiService가 토스해주면 여기서 받음
    public String handle(String userInput, UserSession s, String userId) {

        if (userInput == null)
            return "입력을 다시 말씀해주세요.";

        String normalized = userInput.trim().toLowerCase();

        // 0순위: 공통 탈출 명령어 점검
        String global = checkGlobalCommands(normalized, s);
        if (global != null)
            return global;

        // ==========================================================
        // STEP 1: 완전 초기 단계 (지점 고를 준비)
        // ==========================================================
        if (s.getStep() == UserSession.Step.IDLE) {

            // 오늘볼건지 내일볼건지 날짜부터 메모 (나중에 스케줄 찾을때 써야됨)
            String dateFilter = extractDateFilter(userInput);
            if (dateFilter != null) {
                s.getBookingContext().put("dateFilter", dateFilter);
            } else {
                s.getBookingContext().putIfAbsent("dateFilter", "today");
            }

            // 거리순 정렬을 하려면 이 사람 집주소가 어딘지 알아야 함
            MemberDTO member = userMapper.findById(userId);
            if (member == null)
                return "회원 정보를 찾을 수 없습니다.";
            String userAddress = member.getAddress();

            // 영화 서버한테 "일단 너네 지점 리스트 다 내놔바" 요청
            Map<String, Object> res = intentService.processIntent("movie_booking_step1", Map.of());
            List<Map<String, Object>> cinemas = safeList(res.get("cinemas"));

            // 카카오맵 한테 "강남구 영화관 찾아줘" 처럼 장소 키워드가 있는지 검사
            String keyword = kakaoLocalService.extractPlaceKeyword(userInput);

            Map<String, Object> coord;

            if (keyword != null) {
                // 특정 지역을 말했으면 그 지역 위도/경도를 찾음
                coord = kakaoLocalService.searchCoordinate(keyword);

                if (coord == null) {
                    // 키워드가 엉터리라 못 찾았으면 안전빵으로 내 기본 집주소로 검색
                    coord = kakaoLocalService.searchCoordinate(userAddress);
                }

            } else {
                // 아무 말 없으면 걍 내 집주소 기준
                coord = kakaoLocalService.searchCoordinate(userAddress);
            }

            // 카카오 API 좌표를 기준으로 아까 얻어온 모든 영화관 리스트를 '가까운 순서'로 싹 정렬함
            List<Map<String, Object>> sorted = kakaoLocalService.sortCinemasByDistance(coord, cinemas);

            // 다음 턴에 사용자가 1번을 골랐을 때 그게 뭔지 알기 위해 세션에 정렬된 리스트 저장
            s.setLastCinemas(sorted);
            // 상태를 "지점 선택 대기중"으로 진급시킴
            s.setStep(UserSession.Step.BRANCH_SELECT);

            return formatter.formatCinemas(sorted)
                    + "\n방문하실 지점 번호를 입력해주세요. 예) 1번";
        }

        // ==========================================================
        // STEP 2: 지점 선택 받고 -> 스케줄 목록 보여주기
        // ==========================================================
        if (s.getStep() == UserSession.Step.BRANCH_SELECT) {

            // 이때 갑자기 "아 내일걸로 변경할래" 할 수도 있으니 날짜 필터 한 번 더 확인
            String dateFilter = extractDateFilter(userInput);
            if (dateFilter != null)
                s.getBookingContext().put("dateFilter", dateFilter);

            // 무어라 적었든 숫자만 쏙 골라와서 타당한지 검사
            Integer idx = resolveIndexFromInput(userInput, s.getLastCinemas().size());
            if (idx == null) {
                return "지점 번호를 다시 입력해주세요.\n\n"
                        + "또는 다른 기능을 원하시면 말해주세요.\n예시: \"예매 조회\", \"자전거 예약\"";
            }

            // 유저가 고른(1번 = 인덱스 0) 영화관 객체 꺼내옴
            Map<String, Object> selected = s.getLastCinemas().get(idx - 1);

            String branchNum = String.valueOf(selected.get("branch_num"));
            String branchName = String.valueOf(selected.get("branch_name"));

            // 안 날아가게 메모장에 단단히 적어둠
            s.getBookingContext().put("branchNum", branchNum);
            s.getBookingContext().put("branchName", branchName);

            // 영화 서버한테 "이 지점, 이 날짜에 상영하는 스케줄(영화목록+시간) 다 줘!" 요청
            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step2",
                    Map.of(
                            "branchNum", branchNum,
                            "dateFilter", s.getBookingContext().getOrDefault("dateFilter", "today")));

            List<Map<String, Object>> schedules = safeList(res.get("movies"));
            s.setLastMovies(schedules);

            // 상태를 "스케줄 선택 대기중"으로 진급
            s.setStep(UserSession.Step.MOVIE_SELECT);

            return "지점을 선택했습니다!\n"
                    + "지점: " + branchName + "\n\n"
                    + formatter.formatSchedules(schedules)
                    + "\n예매할 스케줄 번호를 입력해주세요. 예) 2번";
        }

        // ==========================================================
        // STEP 3: 스케줄 선택 받고 -> 잔여 좌석 보여주기
        // ==========================================================
        if (s.getStep() == UserSession.Step.MOVIE_SELECT) {

            // 몇 번째 스케줄 골랐는지 체크
            Integer idx = resolveIndexFromInput(userInput, s.getLastMovies().size());
            if (idx == null) {
                return "스케줄 번호를 다시 입력해주세요.\n또는 \"예매 조회\", \"자전거\" 같은 다른 기능을 말해주세요.";
            }

            Map<String, Object> sel = s.getLastMovies().get(idx - 1);

            // 이번엔 스케줄 번호랑 영화 제목 메모
            s.getBookingContext().put("scheduleNum", String.valueOf(sel.get("scheduleNum")));
            s.getBookingContext().put("movieTitle", String.valueOf(sel.get("movieTitle")));

            // 영화 서버한테 "이 스케줄 좌석 배치도랑 누가 앉았는지 상태 다 내놔" 요청
            Map<String, Object> res = intentService.processIntent(
                    "movie_booking_step3",
                    Map.of("scheduleNum", sel.get("scheduleNum")));

            List<Map<String, Object>> seats = safeList(res.get("seats"));
            s.setLastSeats(seats);

            // 상태 진급
            s.setStep(UserSession.Step.SEAT_SELECT);

            //  매우 중요: 프론트엔드 리액트가 이 응답을 받을 때, 밑에 숨겨놓은 JSON 문구를 파싱해서
            // 모달창에 진짜 좌석 UI를 예쁘게 그려주도록 트리거 역할을 하는 특수 로직임
            String hiddenJson = String.format("{\"scheduleNum\":%s}", sel.get("scheduleNum"));

            return "**" + sel.get("movieTitle") + "** 영화를 선택하셨네요!\n\n"
                    + formatter.formatSeats(seats) // 텍스트로도 좌석 보여줌
                    + "\n원하시는 좌석을 입력해주세요. (예: A3)\n\n"
                    + "좌석 배치도를 보고 싶으시면 \"상세 좌석\"이라고 말씀해주세요!"
                    + "\n" + hiddenJson;
        }

        // ==========================================================
        // STEP 4: 좌석 선택 받고 -> 결제 대기
        // ==========================================================
        if (s.getStep() == UserSession.Step.SEAT_SELECT) {

            // "A3, A4" 같은 식으로 두자리 이상 고를수도 있으니 쉼표나 공백 기준으로 쪼갬
            String[] inputs = userInput.split("[,\\s]+");
            List<Integer> seatCodes = new ArrayList<>();
            List<String> seatLabels = new ArrayList<>();
            int totalAmount = 0;
            String phoneNumber = null;

            // 결제할 때 넣을 본인 핸드폰 번호 미리 빼놓음 (회원 DB에서)
            MemberDTO member = userMapper.findById(userId);
            if (member == null)
                return "회원 정보를 찾을 수 없습니다.";
            phoneNumber = member.getPhoneNum();

            // 쪼갠 좌석 여러개를 반복 돌면서 하나씩 유효한지 빡세게 검사함
            for (String rawInput : inputs) {
                String seatInput = rawInput.trim().toUpperCase(); // a3 쳤어도 A3로 변환
                if (seatInput.isEmpty())
                    continue;

                // 내가 그 스케줄에 있는 좌석이 맞는지 찾아봄
                Map<String, Object> seat = findSeatByLabel(s.getLastSeats(), seatInput);
                if (seat == null)
                    return "좌석 '" + seatInput + "'을(를) 찾을 수 없습니다. 다시 입력해주세요.";

                // 통로에 앉겠다고 하면 컷
                int isAisle = 0;
                if (seat.get("is_aisle") != null) {
                    isAisle = Integer.parseInt(String.valueOf(seat.get("is_aisle")));
                }

                if (isAisle == 1) {
                    return "선택하신 '" + seatInput + "' 좌석은 통로입니다. 다른 좌석을 선택해주세요.";
                }

                // 남이 먼저 홀드했거나 이미 돈내고 예약한 자리면 컷
                if (Boolean.parseBoolean(String.valueOf(seat.get("reserved")))) {
                    return "'" + seatInput + "' 좌석은 이미 예약되었습니다. 다른 좌석을 선택해주세요.";
                }

                int seatCode = Integer.parseInt(String.valueOf(seat.get("seat_code")));

                // 가격 정보가 없으면 기본값 12,000원으로 세팅
                Object priceObj = seat.get("price");
                int pricePerSeat = (priceObj instanceof Number) ? ((Number) priceObj).intValue() : 12000;

                // 통과된 놈들은 결제바구니 리스트에 쏙쏙 담음
                seatCodes.add(seatCode);
                seatLabels.add(seatInput);
                totalAmount += pricePerSeat;
            }

            if (seatCodes.isEmpty()) {
                return "좌석을 입력해주세요.";
            }

            // 세션에 최종 결제할 품목들을 기록해둠
            s.getBookingContext().put("seatCodes", seatCodes);
            s.getBookingContext().put("phoneNumber", phoneNumber);
            s.getBookingContext().put("seatLabels", seatLabels);
            s.getBookingContext().put("amount", totalAmount);

            //  프론트엔드가 토스페이먼츠 연동을 띄우기 위해서 읽어갈 숨김 JSON 데이터
            String jsonData = String.format(
                    "{\"actionType\":\"PAYMENT_CONFIRM\",\"amount\":%d,\"phone\":\"%s\",\"paymentType\":\"MOVIE\"}",
                    totalAmount, phoneNumber);

            s.setStep(UserSession.Step.MOVIE_PAYMENT_CONFIRM);

            return "좌석 선택이 완료되었습니다!\n\n"
                    + "선택한 좌석: " + String.join(", ", seatLabels) + "\n"
                    + "총 금액: " + totalAmount + "원\n"
                    + jsonData;
        }

        // ==========================================================
        // STEP 5: 최종 예매 확정 (결제 모듈에서 성공 돌고 돌아왔을 때 부름)
        // ==========================================================
        if (s.getStep() == UserSession.Step.MOVIE_PAYMENT_CONFIRM) {

            // 사용자가 말로 "결제" 라고 치거나, 프론트에서 버튼 누르면 "confirm" 키워드를 쏴줌
            if (userInput.toLowerCase().contains("결제") || userInput.toLowerCase().contains("confirm")) {

                String scheduleNum = String.valueOf(s.getBookingContext().get("scheduleNum"));
                String phoneNumber = String.valueOf(s.getBookingContext().get("phoneNumber"));

                // 자리를 3개 예약하든 1개 예약하든 시스템상 한 번의 거래(트랜잭션)로 묶어버리려고 그룹 ID를 만듦
                String bookingGroupId = "BG" + System.currentTimeMillis();

                @SuppressWarnings("unchecked")
                List<Integer> seatCodes = (List<Integer>) s.getBookingContext().get("seatCodes");

                // 과거 버전 호환장치 (seatCode 단일 키로 들어올까봐 방어코드)
                if (seatCodes == null || seatCodes.isEmpty()) {
                    if (s.getBookingContext().containsKey("seatCode")) {
                        seatCodes = new ArrayList<>();
                        seatCodes.add((Integer) s.getBookingContext().get("seatCode"));
                    } else {
                        return "예약할 좌석 정보가 없습니다. 다시 시도해주세요.";
                    }
                }

                int successCount = 0;
                StringBuilder failMsg = new StringBuilder();

                // 모아둔 좌석 번호를 돌면서 영화 서버한테 "진짜로 내 자리 확정 박아라" 라고 하나씩 요청
                for (Integer code : seatCodes) {
                    Map<String, Object> res = intentService.processIntent(
                            "movie_booking_step4",
                            Map.of(
                                    "scheduleNum", scheduleNum,
                                    "seatCode", code,
                                    "phoneNumber", phoneNumber,
                                    "bookingGroupId", bookingGroupId));

                    if (res.containsKey("message")) {
                        successCount++; // 성공하면 카운트업
                    } else {
                        // 중간에 누구 하나 자리 뺏겼으면 에러 메시지에 사유를 담음
                        failMsg.append("좌석(ID:").append(code).append(") 실패: ").append(res.getOrDefault("error", "오류"))
                                .append("\n");
                    }
                }

                // 한 자리라도 건졌으면 결제 승인 처리로 넘어감
                if (successCount > 0) {
                    int totalAmount = Integer.parseInt(String.valueOf(s.getBookingContext().get("amount")));

                    // "이 예약그룹은 최종 컨펌 완료됐다" 고 영화 서버에 쐐기를 박음
                    intentService.processIntent("movie_booking_finalize", Map.of(
                            "scheduleNum", scheduleNum,
                            "phoneNumber", phoneNumber,
                            "totalAmount", totalAmount));

                    // 이메일 수신을 'Y'로 켜둔 회원한테만 예매 완료 내역을 진짜 이메일로 발송함
                    try {
                        MemberDTO member = userMapper.findById(userId);
                        if (member != null && "Y".equals(member.getNotificationStatus())) {
                            String movieTitle = String.valueOf(s.getBookingContext().get("movieTitle"));
                            String branchName = String.valueOf(s.getBookingContext().get("branchName"));
                            @SuppressWarnings("unchecked")
                            List<String> seatLabels = (List<String>) s.getBookingContext().get("seatLabels");
                            String seats = String.join(", ", seatLabels);

                            String showtime = s.getBookingContext().getOrDefault("showtime", "예약 내역에서 확인").toString();

                            emailService.sendMovieReservationEmail(
                                    member.getEmail(),
                                    movieTitle,
                                    showtime + " (" + branchName + ")",
                                    seats,
                                    totalAmount);
                        }
                    } catch (Exception e) {
                        // 메일 서버가 죽었어도 예매는 된 거니까 에러 내지 않고 로그만 남기고 쿨하게 넘어감
                        java.util.logging.Logger.getLogger(getClass().getName())
                                .warning("이메일 발송 실패: " + e.getMessage());
                    }

                    // 모든 볼일이 끝났으니 메모장 다 부셔버리고 처음(IDLE) 시작 상태로 리셋
                    s.reset();

                    String msg = "총 " + successCount + "개의 좌석 예매가 완료되었습니다!";
                    if (failMsg.length() > 0) {
                        msg += "\n\n일부 좌석 예약 실패:\n" + failMsg.toString();
                    }

                    return msg + "\n\n"
                            + "상세 내역은 사이드바의 [예약 내역] 페이지에서 확인하실 수 있습니다.\n"
                            + "또 도와드릴까요?";
                }

                // 한 자리도 못 건졌을 때
                s.reset();
                return "예매 실패:\n" + failMsg.toString();
            } else {
                return "결제를 진행해 주시거나, 결제를 취소하시려면 '취소'를 입력해 주세요.";
            }
        }

        return "처리할 수 없는 상태입니다. 다시 시도해주세요.";
    }

}
