package fpt.swp391.GlucoTrackAlert.scheduler;

import fpt.swp391.GlucoTrackAlert.model.reminder.Duy_HealthReminder;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.reminder.Duy_ReminderRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class ReminderScheduler {

    @Autowired
    private Duy_ReminderRepository reminderRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private EmailService emailService;

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy");

    /**
     * Chạy mỗi phút, quét các reminder đã đến giờ mà chưa gửi email
     */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void sendDueReminders() {
        List<Duy_HealthReminder> dueList =
                reminderRepository.findDueReminders(LocalDateTime.now());

        for (Duy_HealthReminder reminder : dueList) {
            try {
                Optional<Patient> patientOpt =
                        patientRepository.findByIdWithUser(reminder.getPatientId());

                if (patientOpt.isEmpty()) {
                    // Patient không tồn tại, đánh dấu sent để không retry mãi
                    markSent(reminder);
                    continue;
                }

                Patient patient = patientOpt.get();
                String email = patient.getUser().getEmail();
                String patientName = patient.getFullName();

                String subject = buildSubject(reminder);
                String htmlBody = buildHtmlBody(reminder, patientName);

                emailService.sendHtmlMessage(email, subject, htmlBody);
                markSent(reminder);

                System.out.println("[ReminderScheduler] Đã gửi email nhắc nhở ID="
                        + reminder.getId() + " → " + email);

            } catch (Exception e) {
                System.err.println("[ReminderScheduler] Lỗi gửi reminder ID="
                        + reminder.getId() + ": " + e.getMessage());
                // Không markSent → sẽ retry lần sau
            }
        }
    }

    private void markSent(Duy_HealthReminder reminder) {
        reminder.setIsSent(true);
        reminder.setSentAt(LocalDateTime.now());

        boolean repeats = reminder.getRepeatType() != null && !"NONE".equals(reminder.getRepeatType());
        LocalDateTime next = repeats ? nextReminderTime(reminder) : null;

        // Nếu reminder có endDate (vd: đơn thuốc có ngày kết thúc) và lần nhắc tiếp theo
        // đã vượt qua endDate đó, dừng lặp lại để tránh nhắc nhở mãi mãi sau khi thuốc hết.
        boolean pastEndDate = reminder.getEndDate() != null
                && next != null
                && next.toLocalDate().isAfter(reminder.getEndDate());

        if (!repeats || pastEndDate) {
            reminder.setStatus("COMPLETED");
        } else {
            // Lặp lại: cập nhật reminderTime sang lần tiếp theo và reset isSent
            reminder.setReminderTime(next);
            reminder.setIsSent(false);
        }

        reminderRepository.save(reminder);
    }

    /**
     * Tính thời gian lần nhắc tiếp theo dựa vào repeatType
     */
    private LocalDateTime nextReminderTime(Duy_HealthReminder reminder) {
        LocalDateTime current = reminder.getReminderTime();
        LocalDateTime now = LocalDateTime.now();
        String repeatType = reminder.getRepeatType();

        LocalDateTime next = switch (repeatType) {
            case "DAILY"   -> current.plusDays(1);
            case "WEEKLY"  -> current.plusWeeks(1);
            case "MONTHLY" -> current.plusMonths(1);
            default        -> current;
        };

        // Nếu reminder bị trễ nhiều hơn 1 chu kỳ (server down, data cũ...),
        // next vẫn có thể <= now → nhảy tiếp tới lần kế tiếp SAU thời điểm hiện tại
        // để tránh gửi email dồn dập mỗi lần scheduler chạy (mỗi phút).
        while (!next.isAfter(now)) {
            next = switch (repeatType) {
                case "DAILY"   -> next.plusDays(1);
                case "WEEKLY"  -> next.plusWeeks(1);
                case "MONTHLY" -> next.plusMonths(1);
                default        -> next.plusMinutes(1); // an toàn, tránh vòng lặp vô hạn
            };
        }

        return next;
    }

    private String buildSubject(Duy_HealthReminder reminder) {
        String icon = switch (reminder.getReminderType() != null ? reminder.getReminderType() : "") {
            case "MEDICATION"   -> "💊";
            case "BLOOD_SUGAR"  -> "🩸";
            case "MEAL"         -> "🍽️";
            case "EXERCISE"     -> "🏃";
            case "DOCTOR_VISIT" -> "🏥";
            default             -> "📌";
        };
        return icon + " GlucoTrack nhắc nhở: " + reminder.getTitle();
    }

    private String buildHtmlBody(Duy_HealthReminder reminder, String patientName) {
        String typeLabel = switch (reminder.getReminderType() != null ? reminder.getReminderType() : "") {
            case "MEDICATION"   -> "Uống thuốc";
            case "BLOOD_SUGAR"  -> "Đo đường huyết";
            case "MEAL"         -> "Bữa ăn";
            case "EXERCISE"     -> "Tập thể dục";
            case "DOCTOR_VISIT" -> "Khám bác sĩ";
            default             -> "Nhắc nhở";
        };

        String timeStr = reminder.getReminderTime() != null
                ? reminder.getReminderTime().format(DISPLAY_FMT)
                : "";

        String messageBlock = (reminder.getMessage() != null && !reminder.getMessage().isBlank())
                ? "<p style='margin:12px 0;color:#374151;font-size:15px;line-height:1.6'>"
                  + escapeHtml(reminder.getMessage()) + "</p>"
                : "";

        String repeatNote = "";
        if (reminder.getRepeatType() != null && !"NONE".equals(reminder.getRepeatType())) {
            String repeatLabel = switch (reminder.getRepeatType()) {
                case "DAILY"   -> "Hàng ngày";
                case "WEEKLY"  -> "Hàng tuần";
                case "MONTHLY" -> "Hàng tháng";
                default        -> "";
            };
            repeatNote = "<p style='margin:8px 0;color:#6b7280;font-size:13px'>🔁 Lặp lại: "
                    + repeatLabel + "</p>";
        }

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f3f4f6;font-family:'Segoe UI',Arial,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:32px 16px">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:16px;overflow:hidden;
                                box-shadow:0 4px 24px rgba(0,0,0,0.08)">

                    <!-- HEADER -->
                    <tr>
                      <td style="background:linear-gradient(135deg,#005b99,#0077c2);
                                 padding:28px 32px;text-align:center">
                        <div style="font-size:36px;margin-bottom:8px">🩺</div>
                        <div style="color:#fff;font-size:22px;font-weight:700;
                                    letter-spacing:0.5px">GlucoTrack</div>
                        <div style="color:rgba(255,255,255,0.8);font-size:13px;
                                    margin-top:4px">Nhắc nhở sức khoẻ</div>
                      </td>
                    </tr>

                    <!-- BODY -->
                    <tr>
                      <td style="padding:32px">
                        <p style="margin:0 0 6px;color:#6b7280;font-size:14px">
                          Xin chào, <strong style="color:#111827">%s</strong>
                        </p>
                        <p style="margin:0 0 24px;color:#374151;font-size:15px">
                          Đây là lời nhắc nhở sức khoẻ từ GlucoTrack:
                        </p>

                        <!-- REMINDER CARD -->
                        <div style="background:#eff6ff;border-left:5px solid #005b99;
                                    border-radius:0 12px 12px 0;padding:20px 24px;
                                    margin-bottom:20px">
                          <div style="font-size:18px;font-weight:700;color:#1e3a5f;
                                      margin-bottom:8px">%s</div>
                          <div style="display:inline-block;background:#005b99;color:#fff;
                                      font-size:12px;font-weight:600;padding:4px 12px;
                                      border-radius:20px;margin-bottom:10px">%s</div>
                          <p style="margin:10px 0 4px;color:#374151;font-size:14px">
                            🕐 <strong>%s</strong>
                          </p>
                          %s
                          %s
                        </div>

                        <p style="margin:0;color:#9ca3af;font-size:13px;
                                  border-top:1px solid #f3f4f6;padding-top:20px">
                          Email này được gửi tự động bởi hệ thống GlucoTrack.<br>
                          Vui lòng không trả lời email này.
                        </p>
                      </td>
                    </tr>

                    <!-- FOOTER -->
                    <tr>
                      <td style="background:#f9fafb;padding:16px 32px;text-align:center">
                        <p style="margin:0;color:#9ca3af;font-size:12px">
                          © 2026 GlucoTrack — FPT University SWP391
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
                escapeHtml(patientName),
                escapeHtml(reminder.getTitle()),
                typeLabel,
                timeStr,
                messageBlock,
                repeatNote
            );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}