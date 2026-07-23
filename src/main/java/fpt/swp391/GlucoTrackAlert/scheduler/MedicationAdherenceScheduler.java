package fpt.swp391.GlucoTrackAlert.scheduler;

import fpt.swp391.GlucoTrackAlert.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.model.medication.MedicationLog;
import fpt.swp391.GlucoTrackAlert.model.medication.PrescriptionItem;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.medication.MedicationLogRepository;
import fpt.swp391.GlucoTrackAlert.service.NotificationService;
import fpt.swp391.GlucoTrackAlert.service.register.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Trước đây MedicationLog.status có 3 giá trị PENDING/TAKEN/MISSED (xem comment
 * trong entity), nhưng không có chỗ nào trong code thực sự set status =
 * "MISSED" — log chỉ chuyển từ PENDING sang TAKEN khi bệnh nhân tự bấm xác
 * nhận, còn nếu quên uống thì log cứ nằm PENDING mãi mãi. Hậu quả:
 * MedicationServiceImpl.getAdherenceStat() luôn trả về missed = 0 dù bệnh nhân
 * có bỏ liều thật, và bác sĩ/người thân không hề được báo khi bệnh nhân quên
 * uống thuốc.
 *
 * Scheduler này quét định kỳ các log còn PENDING nhưng đã quá giờ uống một
 * khoảng "ân hạn" (GRACE_PERIOD), đánh dấu MISSED, báo cho bệnh nhân, và nếu bỏ
 * lỡ NHIỀU LẦN LIÊN TIẾP cùng một loại thuốc thì báo thêm cho bác sĩ đang phụ
 * trách — vì 1 lần quên thường không đáng lo, nhưng bỏ thuốc liên tục là dấu
 * hiệu bác sĩ nên biết để can thiệp sớm.
 */
@Component
public class MedicationAdherenceScheduler {

    @Autowired
    private MedicationLogRepository logRepo;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DoctorPatientAssignmentRepository assignmentRepo;

    @Autowired
    private EmailService emailService;

    // Thời gian ân hạn sau giờ hẹn uống thuốc trước khi coi là "bỏ lỡ".
    // Chọn 3 tiếng: đủ rộng để không báo oan nếu bệnh nhân uống trễ chút,
    // nhưng vẫn đủ sớm để việc nhắc nhở/can thiệp còn ý nghĩa trong ngày.
    private static final int GRACE_PERIOD_HOURS = 3;

    // Số liều bỏ lỡ LIÊN TIẾP (cùng 1 thuốc) trước khi báo thêm cho bác sĩ phụ trách.
    // 2 liều liên tiếp là ngưỡng hợp lý: đủ để loại trừ "quên 1 lần thông thường",
    // nhưng không chờ quá lâu khiến việc can thiệp mất tác dụng.
    private static final int DOCTOR_ESCALATION_STREAK = 2;

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy");

    // Chạy mỗi 15 phút — đủ nhanh để phát hiện sớm, không quá dày để tốn tài nguyên
    @Transactional
    @Scheduled(fixedDelay = 15 * 60_000)
    public void markMissedDoses() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(GRACE_PERIOD_HOURS);
        List<MedicationLog> overdue = logRepo.findByStatusAndScheduledTimeBefore("PENDING", cutoff);

        for (MedicationLog log : overdue) {
            try {
                log.setStatus("MISSED");
                logRepo.save(log);

                PrescriptionItem item = log.getPrescriptionItem();
                Long patientId = log.getPatient() != null ? log.getPatient().getId() : null;
                Long userId = (log.getPatient() != null && log.getPatient().getUser() != null)
                        ? log.getPatient().getUser().getId() : null;
                if (userId == null || item == null) {
                    continue;
                }

                String time = log.getScheduledTime() != null ? log.getScheduledTime().format(DISPLAY_FMT) : "";
                notificationService.createNotification(
                        userId,
                        "💊 Đã bỏ lỡ 1 liều thuốc",
                        "Bạn chưa xác nhận đã uống " + item.getMedicineName()
                        + (item.getDosage() != null ? " (" + item.getDosage() + ")" : "")
                        + " theo lịch lúc " + time + ". Hãy uống bù sớm nếu còn phù hợp, "
                        + "hoặc liên hệ bác sĩ nếu có thắc mắc.",
                        "MEDICATION_MISSED"
                );

                if (patientId != null) {
                    escalateToDoctorIfNeeded(patientId, item);
                }
            } catch (Exception e) {
                System.err.println("[MedicationAdherenceScheduler] Lỗi xử lý log ID="
                        + log.getId() + ": " + e.getMessage());
            }
        }
    }

    // Đếm số liều MISSED liên tiếp gần nhất của cùng 1 PrescriptionItem (tính từ log mới nhất
    // lùi về trước, dừng ngay khi gặp 1 log không phải MISSED). Nếu đạt ngưỡng, báo bác sĩ
    // đang phụ trách bệnh nhân này (nếu có phân công "active").
    private void escalateToDoctorIfNeeded(Long patientId, PrescriptionItem item) {
        List<MedicationLog> itemLogs = logRepo.findByPrescriptionItemIdOrderByScheduledTimeAsc(item.getId());
        int streak = 0;
        for (int i = itemLogs.size() - 1; i >= 0; i--) {
            if ("MISSED".equals(itemLogs.get(i).getStatus())) {
                streak++;
            } else {
                break;
            }
        }
        if (streak < DOCTOR_ESCALATION_STREAK) {
            return;
        }

        Optional<DoctorPatientAssignment> assignment = assignmentRepo.findFirstByPatientIdAndStatus(patientId, "active");
        if (assignment.isEmpty()) {
            return;
        }
        Doctor doctor = assignment.get().getDoctor();
        if (doctor == null || doctor.getUser() == null) {
            return;
        }

        String patientName = assignment.get().getPatient() != null
                ? assignment.get().getPatient().getFullName() : ("bệnh nhân #" + patientId);
        String alertMessage = patientName + " đã bỏ lỡ " + streak + " liều liên tiếp thuốc "
                + item.getMedicineName() + ". Bác sĩ nên cân nhắc liên hệ hoặc điều chỉnh phác đồ.";

        notificationService.createNotification(
                doctor.getUser().getId(),
                "⚠️ Bệnh nhân bỏ thuốc liên tục",
                alertMessage,
                "MEDICATION_ADHERENCE_ALERT"
        );

        String doctorEmail = doctor.getUser().getEmail();
        if (doctorEmail != null && !doctorEmail.isBlank()) {
            String subject = "⚠️ GlucoTrack: " + patientName + " bỏ thuốc liên tục";
            String html = buildDoctorAlertEmail(doctor.getFullName(), patientName, item, streak);
            // Dùng bản async để không làm chậm vòng quét của scheduler nếu SMTP chậm/lỗi
            emailService.sendHtmlMessageAsync(doctorEmail, subject, html);
        }
    }

    private String buildDoctorAlertEmail(String doctorName, String patientName, PrescriptionItem item, int streak) {
        String safeDoctor = doctorName != null ? escapeHtml(doctorName) : "Bác sĩ";
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f3f4f6;font-family:'Segoe UI',Arial,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0" style="padding:32px 16px">
                <tr><td align="center">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)">
                    <tr>
                      <td style="background:linear-gradient(135deg,#b91c1c,#dc2626);padding:28px 32px;text-align:center">
                        <div style="font-size:36px;margin-bottom:8px">⚠️</div>
                        <div style="color:#fff;font-size:22px;font-weight:700">GlucoTrack</div>
                        <div style="color:rgba(255,255,255,0.85);font-size:13px;margin-top:4px">Cảnh báo tuân thủ điều trị</div>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px">
                        <p style="margin:0 0 6px;color:#6b7280;font-size:14px">
                          Xin chào, <strong style="color:#111827">%s</strong>
                        </p>
                        <div style="background:#fef2f2;border-left:5px solid #dc2626;border-radius:0 12px 12px 0;
                                    padding:20px 24px;margin:16px 0">
                          <div style="font-size:16px;font-weight:700;color:#7f1d1d;margin-bottom:8px">
                            %s đã bỏ lỡ %d liều liên tiếp
                          </div>
                          <p style="margin:6px 0;color:#374151;font-size:14px">💊 Thuốc: <strong>%s</strong></p>
                          <p style="margin:6px 0;color:#374151;font-size:14px">Bệnh nhân chưa xác nhận đã uống thuốc theo đúng lịch nhiều lần liên tiếp trong đơn đang điều trị.</p>
                        </div>
                        <p style="margin:0;color:#374151;font-size:14px">
                          Vui lòng kiểm tra hồ sơ bệnh nhân trên hệ thống và cân nhắc liên hệ trực tiếp hoặc điều chỉnh phác đồ nếu cần.
                        </p>
                        <p style="margin:20px 0 0;color:#9ca3af;font-size:13px;border-top:1px solid #f3f4f6;padding-top:16px">
                          Email này được gửi tự động bởi hệ thống GlucoTrack.<br>Vui lòng không trả lời email này.
                        </p>
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#f9fafb;padding:16px 32px;text-align:center">
                        <p style="margin:0;color:#9ca3af;font-size:12px">© 2026 GlucoTrack — FPT University SWP391</p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(safeDoctor, escapeHtml(patientName), streak, escapeHtml(item.getMedicineName()));
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
