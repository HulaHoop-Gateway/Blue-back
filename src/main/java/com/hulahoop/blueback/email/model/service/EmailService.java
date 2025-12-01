package com.hulahoop.blueback.email.model.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.logging.Logger;

@Service
public class EmailService {

    private static final Logger log = Logger.getLogger(EmailService.class.getName());

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 영화 예약 완료 이메일 발송
     */
    public void sendMovieReservationEmail(String toEmail, String movieTitle, String showtime, String seats,
            int amount) {
        try {
            String subject = "[Hulahoop] 영화 예약 완료";
            String content = createMovieEmailContent(movieTitle, showtime, seats, amount);

            sendHtmlEmail(toEmail, subject, content);
            log.info("✅ 영화 예약 이메일 발송 성공: " + toEmail);
        } catch (Exception e) {
            log.warning("❌ 영화 예약 이메일 발송 실패: " + toEmail + " - " + e.getMessage());
            // 이메일 실패해도 예약은 정상 완료
        }
    }

    /**
     * 자전거 예약 완료 이메일 발송
     */
    public void sendBikeReservationEmail(String toEmail, String bikeName, String rentalTime, String location,
            int amount) {
        try {
            String subject = "[Hulahoop] 자전거 예약 완료";
            String content = createBikeEmailContent(bikeName, rentalTime, location, amount);

            sendHtmlEmail(toEmail, subject, content);
            log.info("✅ 자전거 예약 이메일 발송 성공: " + toEmail);
        } catch (Exception e) {
            log.warning("❌ 자전거 예약 이메일 발송 실패: " + toEmail + " - " + e.getMessage());
            // 이메일 실패해도 예약은 정상 완료
        }
    }

    /**
     * HTML 이메일 발송
     */
    private void sendHtmlEmail(String to, String subject, String content) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true); // true = HTML

        mailSender.send(message);
    }

    /**
     * 영화 예약 이메일 HTML 템플릿
     */
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
                                .emoji { font-size: 24px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1><span class="emoji">🎬</span> 영화 예약 완료</h1>
                                </div>
                                <div class="content">
                                    <p>안녕하세요! Hulahoop입니다.</p>
                                    <p>영화 예약이 성공적으로 완료되었습니다.</p>

                                    <div class="info-box">
                                        <strong>🎞️ 영화 제목</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>📅 상영 시간</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>💺 좌석</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>💰 결제 금액</strong>
                                        <p>%,d원</p>
                                    </div>
                                </div>
                                <div class="footer">
                                    <p>예약 내역은 마이페이지 > 예약 내역에서 확인하실 수 있습니다.</p>
                                    <p>© 2024 Hulahoop. All rights reserved.</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                movieTitle, showtime, seats, amount);
    }

    /**
     * 자전거 예약 이메일 HTML 템플릿
     */
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
                                .emoji { font-size: 24px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1><span class="emoji">🚴</span> 자전거 예약 완료</h1>
                                </div>
                                <div class="content">
                                    <p>안녕하세요! Hulahoop입니다.</p>
                                    <p>자전거 예약이 성공적으로 완료되었습니다.</p>

                                    <div class="info-box">
                                        <strong>🚲 자전거</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>📍 대여 지점</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>⏰ 대여 시간</strong>
                                        <p>%s</p>
                                    </div>

                                    <div class="info-box">
                                        <strong>💰 결제 금액</strong>
                                        <p>%,d원</p>
                                    </div>
                                </div>
                                <div class="footer">
                                    <p>예약 내역은 마이페이지 > 예약 내역에서 확인하실 수 있습니다.</p>
                                    <p>© 2024 Hulahoop. All rights reserved.</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                bikeName, location, rentalTime, amount);
    }
}
