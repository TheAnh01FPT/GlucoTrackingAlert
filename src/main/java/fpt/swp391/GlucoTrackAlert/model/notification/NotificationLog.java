package fpt.swp391.GlucoTrackAlert.model.notification;

import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.model.relative.Relative;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Ghi lịch sử mỗi lần hệ thống gửi email (OTP, kích hoạt, cảnh báo người thân,
 * nhắc nhở định kỳ, v.v.). Nhiệm vụ 2: Ghi nhật ký gửi thông báo hệ thống
 * (notification_logs)
 */
@Entity
@Table(name = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Người nhận email (users.id) – null nếu gửi cho người thân ngoài hệ thống
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Nếu email gửi tới người thân (relatives)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relative_id")
    private Relative relative;

    /**
     * Địa chỉ email thực tế đã gửi
     */
    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    /**
     * Tiêu đề email
     */
    @Column(nullable = false, length = 500)
    private String subject;

    /**
     * Loại email: OTP / ACTIVATION / DANGER_ALERT / REMINDER / RESET_PASSWORD /
     * OTHER
     */
    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    /**
     * Kênh gửi: EMAIL / SMS / PUSH (hiện tại chỉ dùng EMAIL)
     */
    @Column(length = 20)
    @Builder.Default
    private String channel = "EMAIL";

    /**
     * true = gửi thành công, false = thất bại
     */
    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = false;

    /**
     * Mô tả lỗi nếu gửi thất bại
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Nội dung tóm tắt (không lưu toàn bộ HTML để tránh bảng quá lớn)
     */
    @Column(name = "body_summary", columnDefinition = "TEXT")
    private String bodySummary;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
