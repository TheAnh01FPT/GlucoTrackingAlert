package fpt.swp391.GlucoTrackAlert.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_reminders")
public class Duy_HealthReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    // Loại nhắc nhở: MEDICATION, BLOOD_SUGAR, MEAL, EXERCISE, DOCTOR_VISIT, CUSTOM
    @Column(name = "reminder_type", nullable = false, length = 30)
    private String reminderType;

    @NotBlank(message = "Tiêu đề không được để trống")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @NotNull(message = "Thời gian nhắc không được để trống")
    @Column(name = "reminder_time", nullable = false)
    private LocalDateTime reminderTime;

    // Lặp lại: NONE, DAILY, WEEKLY, MONTHLY
    @Column(name = "repeat_type", length = 20)
    private String repeatType = "NONE";

    // Trạng thái: ACTIVE, COMPLETED, CANCELLED
    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "is_sent")
    private Boolean isSent = false;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // Google Calendar event ID (để có thể update/delete trên GG Calendar)
    @Column(name = "google_calendar_event_id", length = 255)
    private String googleCalendarEventId;

    // Liên kết tới PrescriptionItem khi reminder được sinh ra từ đơn thuốc.
    // Dùng để: (1) huỷ/tắt reminder khi đơn thuốc bị CANCELLED,
    // (2) biết reminder này thuộc thuốc nào khi cần tra cứu.
    @Column(name = "prescription_item_id")
    private Long prescriptionItemId;

    // Ngày cuối cùng reminder còn hiệu lực (vd: ngày cuối của đợt uống thuốc).
    // Khi repeatType != NONE, scheduler sẽ KHÔNG lặp lại reminder sau ngày này
    // (tự chuyển sang COMPLETED) để tránh nhắc nhở mãi mãi sau khi thuốc đã hết.
    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    // ==================== CONSTRUCTORS ====================
    public Duy_HealthReminder() {}

    // ==================== LOGIC ====================
    public boolean isOverdue() {
        return reminderTime != null
                && reminderTime.isBefore(LocalDateTime.now())
                && "ACTIVE".equals(status);
    }

    public boolean isUpcoming() {
        LocalDateTime now = LocalDateTime.now();
        return reminderTime != null
                && reminderTime.isAfter(now)
                && reminderTime.isBefore(now.plusHours(24))
                && "ACTIVE".equals(status);
    }

    // ==================== NORMALIZE ====================
    @PrePersist
    @PreUpdate
    public void normalizeData() {
        if (title != null) title = title.trim();
        if (status == null) status = "ACTIVE";
        if (repeatType == null) repeatType = "NONE";
        if (isSent == null) isSent = false;
    }

    // ==================== GETTERS & SETTERS ====================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getReminderTime() { return reminderTime; }
    public void setReminderTime(LocalDateTime reminderTime) { this.reminderTime = reminderTime; }

    public String getRepeatType() { return repeatType; }
    public void setRepeatType(String repeatType) { this.repeatType = repeatType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsSent() { return isSent; }
    public void setIsSent(Boolean isSent) { this.isSent = isSent; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public String getGoogleCalendarEventId() { return googleCalendarEventId; }
    public void setGoogleCalendarEventId(String googleCalendarEventId) { this.googleCalendarEventId = googleCalendarEventId; }

    public Long getPrescriptionItemId() { return prescriptionItemId; }
    public void setPrescriptionItemId(Long prescriptionItemId) { this.prescriptionItemId = prescriptionItemId; }

    public java.time.LocalDate getEndDate() { return endDate; }
    public void setEndDate(java.time.LocalDate endDate) { this.endDate = endDate; }
}