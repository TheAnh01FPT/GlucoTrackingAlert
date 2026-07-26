package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.enums.MetricType;
import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.relative.Relative;
import fpt.swp391.GlucoTrackAlert.repository.notification.NotificationLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.relative.RelativeRepository;
import fpt.swp391.GlucoTrackAlert.service.notification.Duy_NotificationLogService;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Nhiệm vụ 1: Tự động gửi Email cảnh báo cho Người thân khi có Chỉ số nguy
 * hiểm.
 *
 * Luồng xử lý: 1. DailyHealthLogServiceImpl.createLog() / updateLog() gọi
 * {@link #checkAndAlertRelatives(DailyHealthLog)} SAU KHI lưu DB thành công. 2.
 * Service này đối chiếu từng chỉ số với HealthThresholdService. 3. Nếu có ≥ 1
 * chỉ số rơi vào LOW_DANGER hoặc HIGH_DANGER → tìm tất cả người thân
 * (relatives) có notify_enabled = true. 4. Biên soạn email HTML khẩn cấp và gửi
 * tới từng người thân. 5. Ghi NotificationLog cho mỗi lần gửi (thành công hoặc
 * thất bại).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Duy_DangerAlertService {

    private final HealthThresholdService healthThresholdService;
    private final RelativeRepository relativeRepository;
    private final EmailService emailService;
    private final Duy_NotificationLogService notificationLogService;
    private final NotificationLogRepository notificationLogRepository;

    /**
     * Kiểm tra nhật ký sức khỏe và gửi cảnh báo nếu có chỉ số nguy hiểm. Gọi
     * hàm này sau khi lưu DailyHealthLog vào DB.
     *
     * @param log Bản ghi nhật ký vừa lưu (phải có patient được load sẵn)
     *
     * LƯU Ý QUAN TRỌNG: hàm này chạy trong TRANSACTION RIÊNG (REQUIRES_NEW).
     * DailyHealthLogServiceImpl.createLog()/updateLog() gọi hàm này bên trong
     * try/catch, nhưng bản thân try/catch đó KHÔNG đủ để bảo vệ transaction
     * chính: các repository (Spring Data JPA) đều tự mang @Transactional, nên
     * chỉ cần MỘT lệnh gọi repository ở đây (vd. relativeRepository,
     * notificationLogRepository...) ném exception là transaction của
     * createLog()/updateLog() bị đánh dấu rollback-only ngay lập tức — cho dù
     * exception có bị bắt (catch) ở tầng trên hay không. Hậu quả: khi
     * createLog() return, Spring cố commit transaction, phát hiện bị đánh dấu
     * rollback-only, ném UnexpectedRollbackException ra ngoài controller (hiển
     * thị "có lỗi" cho người dùng) VÀ rollback luôn cả bản ghi DailyHealthLog
     * vừa nhập, cùng log lịch sử cảnh báo vừa ghi (nếu có). Bằng cách cho hàm
     * này chạy trong transaction riêng, nếu nó lỗi thì chỉ transaction này
     * rollback (không gửi được cảnh báo lần đó), transaction chính lưu nhật ký
     * sức khỏe vẫn commit bình thường.
     */
    // CHẠY NỀN (@Async): việc gửi mail cảnh báo không cần chặn request
    // sửa/tạo nhật ký sức khỏe của người dùng — DailyHealthLogServiceImpl
    // gọi hàm này SAU KHI đã lưu DB xong, nên có chạy nền hay không cũng
    // không ảnh hưởng gì tới dữ liệu đã lưu. Nhờ vậy trang sửa nhật ký trả
    // kết quả ngay, còn email/log cảnh báo hoàn tất sau đó vài giây.
    // Vì @Async nên exception ở đây không còn bay ngược lên được cho
    // DailyHealthLogServiceImpl bắt như trước — tự try/catch và log ở đây.
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndAlertRelatives(DailyHealthLog healthLog) {
        if (healthLog == null || healthLog.getPatient() == null) {
            return;
        }
        try {
            doCheckAndAlertRelatives(healthLog);
        } catch (Exception e) {
            log.warn("[DangerAlert] Không thể kiểm tra/gửi cảnh báo cho log id={}: {}", healthLog.getId(), e.getMessage());
        }
    }

    private void doCheckAndAlertRelatives(DailyHealthLog healthLog) {
        Long patientId = healthLog.getPatient().getId();
        String patientType = healthLog.getPatient().getPatientType();

        // dangerMessages = plain text → lưu bodySummary hiển thị trên UI (không có thẻ HTML)
        // dangerMessagesHtml = có thẻ HTML → chỉ dùng để build nội dung email gửi người thân
        List<String> dangerMessages = new ArrayList<>();
        List<String> dangerMessagesHtml = new ArrayList<>();

        // --- Kiểm tra đường huyết ---
        if (healthLog.getBloodSugar() != null) {
            String status = healthThresholdService.evaluate(
                    healthLog.getBloodSugar(), patientId, patientType, MetricType.BLOOD_SUGAR);
            if ("LOW_DANGER".equals(status)) {
                dangerMessages.add("Đường huyết QUÁ THẤP: " + healthLog.getBloodSugar() + " mmol/L (nguy cơ hạ đường huyết nghiêm trọng)");
                dangerMessagesHtml.add("🔴 Đường huyết QUÁ THẤP: <b>" + healthLog.getBloodSugar() + " mmol/L</b> (nguy cơ hạ đường huyết nghiêm trọng)");
            } else if ("HIGH_DANGER".equals(status)) {
                dangerMessages.add("Đường huyết QUÁ CAO: " + healthLog.getBloodSugar() + " mmol/L (nguy cơ tăng đường huyết nghiêm trọng)");
                dangerMessagesHtml.add("🔴 Đường huyết QUÁ CAO: <b>" + healthLog.getBloodSugar() + " mmol/L</b> (nguy cơ tăng đường huyết nghiêm trọng)");
            }
        }

        // --- Kiểm tra huyết áp tâm thu ---
        if (healthLog.getSystolic() != null) {
            String status = healthThresholdService.evaluate(
                    BigDecimal.valueOf(healthLog.getSystolic()), patientId, patientType, MetricType.SYSTOLIC);
            if ("LOW_DANGER".equals(status)) {
                dangerMessages.add("Huyết áp tâm thu QUÁ THẤP: " + healthLog.getSystolic() + " mmHg");
                dangerMessagesHtml.add("🔴 Huyết áp tâm thu QUÁ THẤP: <b>" + healthLog.getSystolic() + " mmHg</b>");
            } else if ("HIGH_DANGER".equals(status)) {
                dangerMessages.add("Huyết áp tâm thu QUÁ CAO: " + healthLog.getSystolic() + " mmHg");
                dangerMessagesHtml.add("🔴 Huyết áp tâm thu QUÁ CAO: <b>" + healthLog.getSystolic() + " mmHg</b>");
            }
        }

        // --- Kiểm tra huyết áp tâm trương ---
        if (healthLog.getDiastolic() != null) {
            String status = healthThresholdService.evaluate(
                    BigDecimal.valueOf(healthLog.getDiastolic()), patientId, patientType, MetricType.DIASTOLIC);
            if ("LOW_DANGER".equals(status)) {
                dangerMessages.add("Huyết áp tâm trương QUÁ THẤP: " + healthLog.getDiastolic() + " mmHg");
                dangerMessagesHtml.add("🔴 Huyết áp tâm trương QUÁ THẤP: <b>" + healthLog.getDiastolic() + " mmHg</b>");
            } else if ("HIGH_DANGER".equals(status)) {
                dangerMessages.add("Huyết áp tâm trương QUÁ CAO: " + healthLog.getDiastolic() + " mmHg");
                dangerMessagesHtml.add("🔴 Huyết áp tâm trương QUÁ CAO: <b>" + healthLog.getDiastolic() + " mmHg</b>");
            }
        }

        // Không có chỉ số nguy hiểm → không gửi
        if (dangerMessages.isEmpty()) {
            return;
        }

        // Lấy danh sách người thân có notify_enabled = true
        List<Relative> relatives = relativeRepository.findByPatientIdAndNotifyEnabled(patientId, true);
        if (relatives.isEmpty()) {
            log.warn("[DangerAlert] Bệnh nhân id={} có chỉ số nguy hiểm nhưng không có người thân nào đăng ký nhận thông báo.", patientId);
            return;
        }

        String alertSummary = "Cảnh báo nguy hiểm: " + String.join("; ", dangerMessages);

        // Chống spam: nếu nội dung cảnh báo y hệt lần gần nhất đã gửi THÀNH CÔNG
        // trong cùng ngày hôm nay (vd. bệnh nhân sửa đi sửa lại nhật ký nhiều lần
        // nhưng chỉ số nguy hiểm không đổi), thì bỏ qua, không gửi lại để tránh
        // làm phiền người thân. Nếu nội dung khác (chỉ số mới, mức độ khác...) thì
        // vẫn gửi bình thường vì đó là thông tin mới, không nên bị chặn.
        try {
            var lastAlertOpt = notificationLogRepository
                    .findTopByRelative_Patient_IdAndNotificationTypeAndSuccessTrueOrderBySentAtDesc(
                            patientId, "DANGER_ALERT");
            if (lastAlertOpt.isPresent()) {
                var lastAlert = lastAlertOpt.get();
                boolean sameDay = lastAlert.getSentAt() != null
                        && lastAlert.getSentAt().toLocalDate().equals(java.time.LocalDate.now());
                boolean sameContent = alertSummary.equals(lastAlert.getBodySummary());
                if (sameDay && sameContent) {
                    log.info("[DangerAlert] Bỏ qua gửi trùng lặp cho bệnh nhân id={}: nội dung cảnh báo không đổi so với lần gửi gần nhất trong ngày.", patientId);
                    return;
                }
            }
        } catch (Exception e) {
            // Không để lỗi ở bước kiểm tra chống trùng lặp chặn việc gửi cảnh báo thật sự
            log.warn("[DangerAlert] Không thể kiểm tra lịch sử cảnh báo gần nhất cho bệnh nhân id={}: {}", patientId, e.getMessage());
        }

        String patientName = healthLog.getPatient().getFullName();
        String logDate = healthLog.getLogDate() != null
                ? healthLog.getLogDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "hôm nay";

        String subject = "🚨 CẢNH BÁO KHẨN CẤP: Chỉ số sức khỏe nguy hiểm của " + patientName;
        String htmlBody = buildAlertEmailHtml(patientName, logDate, dangerMessagesHtml,
                healthLog.getBloodSugar(), healthLog.getSystolic(), healthLog.getDiastolic(),
                healthLog.getSymptoms(), healthLog.getNote());

        // Gửi email tới từng người thân
        for (Relative relative : relatives) {
            String recipientEmail = relative.getEmail();
            String errorMsg = null;
            try {
                emailService.sendHtmlMessage(recipientEmail, subject, htmlBody);
                log.info("[DangerAlert] Đã gửi cảnh báo tới người thân '{}' <{}> của bệnh nhân '{}'",
                        relative.getFullName(), recipientEmail, patientName);
            } catch (Exception e) {
                errorMsg = e.getMessage();
                log.error("[DangerAlert] Không thể gửi email tới {}: {}", recipientEmail, errorMsg);
            }

            // Ghi log (Nhiệm vụ 2)
            notificationLogService.log(
                    null,
                    relative,
                    recipientEmail,
                    subject,
                    "DANGER_ALERT",
                    alertSummary,
                    errorMsg
            );
        }
    }

    /**
     * Tạo nội dung email HTML khẩn cấp
     */
    private String buildAlertEmailHtml(String patientName, String logDate,
            List<String> dangerMessages,
            BigDecimal bloodSugar, Integer systolic, Integer diastolic,
            String symptoms, String note) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>");
        sb.append("<style>");
        sb.append("body{font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:20px;}");
        sb.append(".container{max-width:600px;margin:auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.15);}");
        sb.append(".header{background:#c0392b;color:#fff;padding:24px 32px;}");
        sb.append(".header h1{margin:0;font-size:22px;}");
        sb.append(".header p{margin:6px 0 0;font-size:14px;opacity:0.9;}");
        sb.append(".body{padding:28px 32px;}");
        sb.append(".alert-box{background:#fdecea;border-left:5px solid #c0392b;padding:16px 20px;border-radius:4px;margin-bottom:20px;}");
        sb.append(".alert-box p{margin:6px 0;font-size:15px;color:#333;}");
        sb.append(".info-table{width:100%;border-collapse:collapse;margin:16px 0;}");
        sb.append(".info-table td{padding:8px 12px;border:1px solid #e0e0e0;font-size:14px;}");
        sb.append(".info-table td:first-child{background:#f9f9f9;font-weight:bold;width:45%;}");
        sb.append(".footer{background:#f0f0f0;padding:16px 32px;font-size:12px;color:#888;text-align:center;}");
        sb.append("</style></head><body>");
        sb.append("<div class='container'>");

        // Header
        sb.append("<div class='header'>");
        sb.append("<h1>🚨 CẢNH BÁO KHẨN CẤP – Chỉ số sức khỏe nguy hiểm</h1>");
        sb.append("<p>Hệ thống GlucoTrackingAlert đã phát hiện chỉ số nguy hiểm</p>");
        sb.append("</div>");

        // Body
        sb.append("<div class='body'>");
        sb.append("<p>Xin chào,</p>");
        sb.append("<p>Hệ thống ghi nhận chỉ số sức khỏe bất thường của <b>").append(patientName).append("</b>");
        sb.append(" vào ngày <b>").append(logDate).append("</b>. Vui lòng liên hệ ngay với bệnh nhân.</p>");

        // Alert messages
        sb.append("<div class='alert-box'>");
        for (String msg : dangerMessages) {
            sb.append("<p>").append(msg).append("</p>");
        }
        sb.append("</div>");

        // Detail table
        sb.append("<h3 style='color:#333;font-size:15px;margin-bottom:8px;'>📋 Thông tin chi tiết nhật ký</h3>");
        sb.append("<table class='info-table'>");
        sb.append("<tr><td>Bệnh nhân</td><td>").append(patientName).append("</td></tr>");
        sb.append("<tr><td>Ngày ghi</td><td>").append(logDate).append("</td></tr>");
        if (bloodSugar != null) {
            sb.append("<tr><td>Đường huyết</td><td>").append(bloodSugar).append(" mmol/L</td></tr>");
        }
        if (systolic != null) {
            sb.append("<tr><td>Huyết áp tâm thu</td><td>").append(systolic).append(" mmHg</td></tr>");
        }
        if (diastolic != null) {
            sb.append("<tr><td>Huyết áp tâm trương</td><td>").append(diastolic).append(" mmHg</td></tr>");
        }
        if (symptoms != null && !symptoms.isBlank()) {
            sb.append("<tr><td>Triệu chứng</td><td>").append(symptoms).append("</td></tr>");
        }
        if (note != null && !note.isBlank()) {
            sb.append("<tr><td>Ghi chú</td><td>").append(note).append("</td></tr>");
        }
        sb.append("</table>");

        sb.append("<p style='color:#c0392b;font-weight:bold;margin-top:20px;'>⚠️ Đây là cảnh báo tự động từ hệ thống. ");
        sb.append("Nếu bệnh nhân có biểu hiện bất thường, hãy đưa đến cơ sở y tế ngay lập tức.</p>");
        sb.append("</div>");

        // Footer
        sb.append("<div class='footer'>Email này được gửi tự động bởi GlucoTrackingAlert &mdash; SWP391 FPT University.<br>");
        sb.append("Vui lòng không trả lời email này.</div>");
        sb.append("</div></body></html>");

        return sb.toString();
    }
}
