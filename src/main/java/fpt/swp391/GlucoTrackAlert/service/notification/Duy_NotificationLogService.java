package fpt.swp391.GlucoTrackAlert.service.notification;

import fpt.swp391.GlucoTrackAlert.model.notification.NotificationLog;
import fpt.swp391.GlucoTrackAlert.model.relative.Relative;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.notification.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Nhiệm vụ 2: Ghi nhật ký gửi thông báo hệ thống.
 *
 * Cách dùng — chèn vào BẤT KỲ hàm nào gửi mail:
 * <pre>
 *     try {
 *         emailService.sendHtmlMessage(to, subject, html);
 *         notificationLogService.log(user, null, to, subject, "DANGER_ALERT", html, null);
 *     } catch (Exception e) {
 *         notificationLogService.log(user, null, to, subject, "DANGER_ALERT", html, e.getMessage());
 *         throw e;
 *     }
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Duy_NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    /**
     * Ghi một bản ghi log gửi email.
     *
     * @param user User nhận (null nếu gửi tới người thân không có tài khoản)
     * @param relative Relative nhận (null nếu không phải gửi người thân)
     * @param recipientEmail Email thực tế đã gửi
     * @param subject Tiêu đề email
     * @param notificationType Loại: OTP / ACTIVATION / DANGER_ALERT / REMINDER
     * / RESET_PASSWORD / OTHER
     * @param bodySummary Tóm tắt nội dung (tùy chọn)
     * @param errorMessage null nếu thành công; message lỗi nếu thất bại
     */
    public void log(User user,
            Relative relative,
            String recipientEmail,
            String subject,
            String notificationType,
            String bodySummary,
            String errorMessage) {
        try {
            // Tóm tắt body: chỉ giữ 500 ký tự đầu để tránh bảng quá lớn
            String summary = bodySummary;
            if (summary != null && summary.length() > 500) {
                summary = summary.substring(0, 500) + "...";
            }

            NotificationLog entry = NotificationLog.builder()
                    .user(user)
                    .relative(relative)
                    .recipientEmail(recipientEmail)
                    .subject(subject)
                    .notificationType(notificationType)
                    .channel("EMAIL")
                    .success(errorMessage == null)
                    .errorMessage(errorMessage)
                    .bodySummary(summary)
                    .build();

            notificationLogRepository.save(entry);
        } catch (Exception ex) {
            // Không để lỗi log làm ảnh hưởng flow chính
            log.error("Không thể ghi NotificationLog: {}", ex.getMessage());
        }
    }

    /**
     * Lấy danh sách log để hiển thị trên trang admin, có phân trang. Lọc theo
     * loại thông báo (notificationType) và/hoặc theo trạng thái thành công
     * (success) nếu được truyền vào, ngược lại trả về toàn bộ.
     *
     * @param notificationType lọc theo loại (DANGER_ALERT, OTP, REMINDER...),
     * null = không lọc
     * @param onlyFailed true = chỉ lấy log gửi thất bại, false/null = lấy tất
     * cả
     * @param page số trang (bắt đầu từ 0)
     * @param size số dòng mỗi trang
     */
    public Page<NotificationLog> findLogs(String notificationType, Boolean onlyFailed, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));

        if (Boolean.TRUE.equals(onlyFailed)) {
            return notificationLogRepository.findBySuccessFalseOrderBySentAtDesc(pageable);
        }
        if (notificationType != null && !notificationType.isBlank()) {
            return notificationLogRepository.findByNotificationTypeOrderBySentAtDesc(notificationType, pageable);
        }
        return notificationLogRepository.findAll(pageable);
    }

    /**
     * Lịch sử cảnh báo đã gửi cho người thân của 1 bệnh nhân, phục vụ trang
     * "Lịch sử cảnh báo của tôi" mà chính bệnh nhân tự xem. Chỉ lấy đúng loại
     * DANGER_ALERT để tránh lẫn các loại thông báo khác có thể gửi cho người
     * thân trong tương lai (vd nhắc lịch tái khám). Có thể lọc theo khoảng ngày
     * gửi (fromDate/toDate, null = không giới hạn).
     */
    public Page<NotificationLog> findLogsForPatient(Long patientId, java.time.LocalDate fromDate,
            java.time.LocalDate toDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));

        if (fromDate == null && toDate == null) {
            return notificationLogRepository.findByRelative_Patient_IdAndNotificationTypeOrderBySentAtDesc(
                    patientId, "DANGER_ALERT", pageable);
        }

        java.time.LocalDateTime from = (fromDate != null ? fromDate : java.time.LocalDate.of(2000, 1, 1))
                .atStartOfDay();
        java.time.LocalDateTime to = (toDate != null ? toDate : java.time.LocalDate.now())
                .atTime(23, 59, 59);

        return notificationLogRepository.findByRelative_Patient_IdAndNotificationTypeAndSentAtBetweenOrderBySentAtDesc(
                patientId, "DANGER_ALERT", from, to, pageable);
    }

    /**
     * Đếm tổng số cảnh báo thành công/thất bại của 1 bệnh nhân, tính trên TOÀN
     * BỘ dữ liệu khớp bộ lọc (không chỉ trang hiện tại đang hiển thị). Dùng cho
     * hàng thống kê ở đầu trang "Lịch sử cảnh báo của tôi" — nếu dùng
     * logs.content (chỉ 10 dòng/trang) để đếm thì số liệu sẽ sai khi bệnh nhân
     * có nhiều hơn 1 trang lịch sử.
     */
    public long[] countSuccessAndFailForPatient(Long patientId, java.time.LocalDate fromDate, java.time.LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            long success = notificationLogRepository.countByRelative_Patient_IdAndNotificationTypeAndSuccess(
                    patientId, "DANGER_ALERT", true);
            long fail = notificationLogRepository.countByRelative_Patient_IdAndNotificationTypeAndSuccess(
                    patientId, "DANGER_ALERT", false);
            return new long[]{success, fail};
        }

        java.time.LocalDateTime from = (fromDate != null ? fromDate : java.time.LocalDate.of(2000, 1, 1))
                .atStartOfDay();
        java.time.LocalDateTime to = (toDate != null ? toDate : java.time.LocalDate.now())
                .atTime(23, 59, 59);

        long success = notificationLogRepository.countByRelative_Patient_IdAndNotificationTypeAndSuccessAndSentAtBetween(
                patientId, "DANGER_ALERT", true, from, to);
        long fail = notificationLogRepository.countByRelative_Patient_IdAndNotificationTypeAndSuccessAndSentAtBetween(
                patientId, "DANGER_ALERT", false, from, to);
        return new long[]{success, fail};
    }
}
