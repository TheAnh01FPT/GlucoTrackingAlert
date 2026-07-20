package fpt.swp391.GlucoTrackAlert.service.impl.register;

import fpt.swp391.GlucoTrackAlert.model.notification.NotificationLog;
import fpt.swp391.GlucoTrackAlert.repository.notification.NotificationLogRepository;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * EmailService implementation. Nhiệm vụ 2: Sau mỗi lần gửi email (thành công
 * hay thất bại), tự động ghi 1 bản ghi vào notification_logs.
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;
    private final NotificationLogRepository notificationLogRepository;

    public EmailServiceImpl(JavaMailSender emailSender,
            NotificationLogRepository notificationLogRepository) {
        this.emailSender = emailSender;
        this.notificationLogRepository = notificationLogRepository;
    }

    // BUG FIX: Bỏ @Async ở đây — ReminderScheduler tự xử lý exception và retry.
    // Nếu để @Async, exception bị nuốt trên async thread riêng, scheduler không
    // nhận được → không retry, không markSent đúng lúc.
    @Override
    public void sendSimpleMessage(String to, String subject, String text) {
        String errorMsg = null;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            emailSender.send(message);
        } catch (Exception e) {
            errorMsg = e.getMessage();
            throw new RuntimeException("Lỗi gửi email tới " + to + ": " + e.getMessage(), e);
        } finally {
            saveLog(to, subject, "OTHER", text, errorMsg);
        }
    }

    @Override
    public void sendHtmlMessage(String to, String subject, String htmlContent) {
        String errorMsg = null;
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            emailSender.send(message);
        } catch (Exception e) {
            errorMsg = e.getMessage();
            // Ném thẳng ra để caller (ReminderScheduler / DangerAlertService) bắt được và xử lý retry
            throw new RuntimeException("Lỗi gửi email HTML tới " + to + ": " + e.getMessage(), e);
        } finally {
            saveLog(to, subject, resolveType(subject), htmlContent, errorMsg);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    /**
     * Đoán loại email dựa trên tiêu đề để điền notificationType cho đúng.
     * Caller có thể ghi đè bằng Duy_NotificationLogService nếu cần chính xác
     * hơn.
     */
    private String resolveType(String subject) {
        if (subject == null) {
            return "OTHER";
        }
        String s = subject.toUpperCase();
        if (s.contains("OTP") || s.contains("MÃ XÁC NHẬN")) {
            return "OTP";
        }
        if (s.contains("KÍCH HOẠT") || s.contains("ACTIVATION")) {
            return "ACTIVATION";
        }
        if (s.contains("CẢNH BÁO") || s.contains("DANGER") || s.contains("ALERT")) {
            return "DANGER_ALERT";
        }
        if (s.contains("NHẮC NHỞ") || s.contains("REMINDER")) {
            return "REMINDER";
        }
        if (s.contains("ĐẶT LẠI MẬT KHẨU") || s.contains("RESET")) {
            return "RESET_PASSWORD";
        }
        return "OTHER";
    }

    /**
     * Ghi log vào notification_logs (không ném exception nếu lỗi log)
     */
    private void saveLog(String to, String subject, String type, String body, String errorMsg) {
        try {
            String summary = body;
            if (summary != null && summary.length() > 500) {
                summary = summary.substring(0, 500) + "...";
            }

            NotificationLog entry = NotificationLog.builder()
                    .recipientEmail(to)
                    .subject(subject)
                    .notificationType(type)
                    .channel("EMAIL")
                    .success(errorMsg == null)
                    .errorMessage(errorMsg)
                    .bodySummary(summary)
                    .build();

            notificationLogRepository.save(entry);
        } catch (Exception ex) {
            log.error("Không thể ghi NotificationLog: {}", ex.getMessage());
        }
    }

    @Override
    @org.springframework.scheduling.annotation.Async
    public void sendSimpleMessageAsync(String to, String subject, String text) {
        try {
            sendSimpleMessage(to, subject, text);
        } catch (Exception e) {
            // Log exception or handle it since Async exceptions are unhandled by default caller
            System.err.println("Error sending async simple email to " + to + ": " + e.getMessage());
        }
    }

    @Override
    @org.springframework.scheduling.annotation.Async
    public void sendHtmlMessageAsync(String to, String subject, String htmlContent) {
        try {
            sendHtmlMessage(to, subject, htmlContent);
        } catch (Exception e) {
            // Log exception or handle it since Async exceptions are unhandled by default caller
            System.err.println("Error sending async HTML email to " + to + ": " + e.getMessage());
        }
    }
}
