package com.hulahoop.blueback.email.model.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.logging.Logger;

// 회원이 영화나 자전거 예약을 완료했을 때, 안내 메일을 발송해주는 서비스
// application.yml에 등록해둔 구글 SMTP 서버 계정 정보를 끌어다 사용함
@Service
public class EmailService {

    private static final Logger log = Logger.getLogger(EmailService.class.getName());

    // 구글 메일 서버와 직접 통신하며 메세지를 쏘는 스프링부트 내장 헬퍼 객체
    private final JavaMailSender mailSender;

    // 누가 보내는 건지 표시할 발신자 이메일 주소 (우리의 구글 계정)
    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // 영화 예약이 성공적으로 결제까지 끝났을 때 외부(MovieBookingFlowHandler 등)에서 호출하는 메서드
    public void sendMovieReservationEmail(String toEmail, String movieTitle, String showtime, String seats,
            int amount) {
        try {
            String subject = "[Hulahoop] 영화 예약 완료";
            // 아래에 만들어둔 이메일 HTML 양식에 데이터(영화이름, 시간 등)를 쏙쏙 집어넣음
            String content = createMovieEmailContent(movieTitle, showtime, seats, amount);

            // 실제 메일 발송
            sendHtmlEmail(toEmail, subject, content);
            log.info("영화 예약 안내 이메일 발송을 성공했습니다: " + toEmail);
        } catch (Exception e) {
            // 비동기로 돌리거나 에러를 먹어버리는 이유:
            // 결제랑 DB 예약 처리는 다 성공했는데, 단순히 메일 전송이 실패했다고 해서
            // 웹페이지에 "예약 실패" 라고 띄우면 사용자가 당황함. 그래서 메일 전송 실패는 시스템 로그만 남기고 넘기게 처리.
            log.warning("영화 예약 안내 이메일 발송을 실패했습니다: " + toEmail + " - " + e.getMessage());
        }
    }

    // 자전거 대여 결제가 끝났을 때 호출되는 메서드
    public void sendBikeReservationEmail(String toEmail, String bikeName, String rentalTime, String location,
            int amount) {
        try {
            String subject = "[Hulahoop] 자전거 예약 완료";
            String content = createBikeEmailContent(bikeName, rentalTime, location, amount);

            sendHtmlEmail(toEmail, subject, content);
            log.info("자전거 예약 안내 이메일 발송을 성공했습니다: " + toEmail);
        } catch (Exception e) {
            log.warning("자전거 예약 안내 이메일 발송을 실패했습니다: " + toEmail + " - " + e.getMessage());
        }
    }

    // 실질적인 MIME(멀티파트) 메일 객체를 조립하고 발송 버튼을 누르는 코어 로직
    private void sendHtmlEmail(String to, String subject, String content) throws MessagingException {
        // 단순 텍스트가 아니라 테이블이나 색상이 들어간 HTML을 쏘려면 MimeMessage를 써야 함
        MimeMessage message = mailSender.createMimeMessage();

        // 두번째 인자 true는 멀티파트 플래그, 세번째는 한글 안 깨지게 UTF-8 지정
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        // true를 날리면 메일 클라이언트(지메일, 네이버 등)가 이 문자열을 텍스트가 아닌 웹문서(HTML)로 그려줌
        helper.setText(content, true);

        mailSender.send(message);
    }

    // HTML 양식 (영화용) - 자바 텍스트블록(""")을 이용해서 편하게 양식 작성
    private String createMovieEmailContent(String movieTitle, String showtime, String seats, int amount) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>
                                body { font-family: 'Malgun Gothic', sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                                .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                                .header { background: linear-gradient(135deg, #4B90FF, #845BFF); color: white; padding: 30px; text-align: center; }
                                .header h1 { margin: 0; font-size: 24px; }
                                .content { padding: 30px; }
                                .info-box { background: #f8f9fa; border-left: 4px solid #4B90FF; padding: 15px; margin: 15px 0; }
                                .info-box strong { color: #333; display: block; margin-bottom: 5px; font-size: 14px; }
                                .info-box p { color: #666; margin: 0; font-size: 16px; }
                                .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 12px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>[영화 예약 완료]</h1>
                                </div>
                                <div class="content">
                                    <p>안녕하세요! Hulahoop입니다.</p>
                                    <p>영화 예약이 성공적으로 완료되었습니다.</p>

                                    <div class="info-box">
                                        <strong>영화 제목</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>상영 시간</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>좌석 정보</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>결제 금액</strong>
                                        <p>%,d원</p>
                                    </div>
                                </div>
                                <div class="footer">
                                    <p>예약 내역은 마이페이지 > 예약 내역에서 확인하실 수 있습니다.</p>
                                    <p>(C) 2024 Hulahoop. All rights reserved.</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                movieTitle, showtime, seats, amount);
    }

    // HTML 양식 (자전거용)
    private String createBikeEmailContent(String bikeName, String rentalTime, String location, int amount) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>
                                body { font-family: 'Malgun Gothic', sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                                .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                                .header { background: linear-gradient(135deg, #4B90FF, #845BFF); color: white; padding: 30px; text-align: center; }
                                .header h1 { margin: 0; font-size: 24px; }
                                .content { padding: 30px; }
                                .info-box { background: #f8f9fa; border-left: 4px solid #845BFF; padding: 15px; margin: 15px 0; }
                                .info-box strong { color: #333; display: block; margin-bottom: 5px; font-size: 14px; }
                                .info-box p { color: #666; margin: 0; font-size: 16px; }
                                .footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 12px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>[자전거 예약 완료]</h1>
                                </div>
                                <div class="content">
                                    <p>안녕하세요! Hulahoop입니다.</p>
                                    <p>자전거 이용 예약이 성공적으로 완료되었습니다.</p>

                                    <div class="info-box">
                                        <strong>이용 수단</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>대여 지점</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>대여 시간</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>결제 금액</strong>
                                        <p>%,d원</p>
                                    </div>
                                </div>
                                <div class="footer">
                                    <p>예약 내역은 마이페이지 > 예약 내역에서 확인하실 수 있습니다.</p>
                                    <p>(C) 2024 Hulahoop. All rights reserved.</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                bikeName, location, rentalTime, amount);
    }
}
