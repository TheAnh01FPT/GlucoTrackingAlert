package fpt.swp391.GlucoTrackAlert.repository.notification;

import fpt.swp391.GlucoTrackAlert.model.notification.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * Lịch sử gửi của 1 user
     */
    Page<NotificationLog> findByUserIdOrderBySentAtDesc(Long userId, Pageable pageable);

    /**
     * Lịch sử gửi tới 1 người thân
     */
    List<NotificationLog> findByRelativeIdOrderBySentAtDesc(Long relativeId);

    /**
     * Lịch sử theo loại
     */
    Page<NotificationLog> findByNotificationTypeOrderBySentAtDesc(String notificationType, Pageable pageable);

    /**
     * Chỉ xem log thất bại
     */
    Page<NotificationLog> findBySuccessFalseOrderBySentAtDesc(Pageable pageable);

    /**
     * Lịch sử cảnh báo đã gửi cho người thân của 1 bệnh nhân cụ thể (dùng để
     * bệnh nhân tự xem lịch sử cảnh báo của chính mình).
     */
    Page<NotificationLog> findByRelative_Patient_IdOrderBySentAtDesc(Long patientId, Pageable pageable);

    /**
     * Như trên nhưng lọc đúng loại thông báo (vd: chỉ lấy DANGER_ALERT, không
     * lẫn các loại thông báo khác có thể gửi cho người thân trong tương lai).
     */
    Page<NotificationLog> findByRelative_Patient_IdAndNotificationTypeOrderBySentAtDesc(
            Long patientId, String notificationType, Pageable pageable);

    /**
     * Như trên, có thêm lọc theo khoảng thời gian gửi (dùng cho bộ lọc ngày
     * trên trang lịch sử cảnh báo của bệnh nhân).
     */
    Page<NotificationLog> findByRelative_Patient_IdAndNotificationTypeAndSentAtBetweenOrderBySentAtDesc(
            Long patientId, String notificationType, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Lấy bản ghi cảnh báo (thành công) gần nhất đã gửi cho người thân của 1
     * bệnh nhân, dùng để chống gửi trùng lặp nội dung cảnh báo trong cùng một
     * ngày (vd. bệnh nhân sửa đi sửa lại nhật ký nhiều lần).
     */
    Optional<NotificationLog> findTopByRelative_Patient_IdAndNotificationTypeAndSuccessTrueOrderBySentAtDesc(
            Long patientId, String notificationType);

    /**
     * Đếm tổng số cảnh báo thành công/thất bại của 1 bệnh nhân (toàn bộ dữ
     * liệu, không chỉ trang hiện tại) — dùng cho hàng thống kê trên trang "Lịch
     * sử cảnh báo của tôi".
     */
    long countByRelative_Patient_IdAndNotificationTypeAndSuccess(
            Long patientId, String notificationType, Boolean success);

    /**
     * Như trên, có thêm lọc theo khoảng thời gian gửi (khớp với bộ lọc ngày
     * trên trang lịch sử cảnh báo).
     */
    long countByRelative_Patient_IdAndNotificationTypeAndSuccessAndSentAtBetween(
            Long patientId, String notificationType, Boolean success, LocalDateTime from, LocalDateTime to);
}
