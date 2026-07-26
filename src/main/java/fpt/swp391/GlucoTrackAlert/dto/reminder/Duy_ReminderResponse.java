package fpt.swp391.GlucoTrackAlert.dto.reminder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Duy_ReminderResponse {

    private Long id;
    private Long patientId;
    private String reminderType;
    private String reminderTypeLabel; // Tên hiển thị tiếng Việt
    private String reminderTypeIcon;  // Emoji icon
    private String title;
    private String message;
    private LocalDateTime reminderTime;
    private String reminderTimeFormatted;
    private String repeatType;
    private String repeatTypeLabel;
    private String status;
    private Boolean isSent;
    private LocalDateTime sentAt;
    private String googleCalendarEventId;
    private boolean synced; // true nếu đã có google_calendar_event_id

    // ==================== STATIC FACTORY ====================
    public static Duy_ReminderResponse from(fpt.swp391.GlucoTrackAlert.model.reminder.Duy_HealthReminder r) {
        Duy_ReminderResponse res = new Duy_ReminderResponse();
        res.id = r.getId();
        res.patientId = r.getPatientId();
        res.reminderType = r.getReminderType();
        res.reminderTypeLabel = mapTypeLabel(r.getReminderType());
        res.reminderTypeIcon = mapTypeIcon(r.getReminderType());
        res.title = r.getTitle();
        res.message = r.getMessage();
        res.reminderTime = r.getReminderTime();
        if (r.getReminderTime() != null) {
            res.reminderTimeFormatted = r.getReminderTime()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
        res.repeatType = r.getRepeatType();
        res.repeatTypeLabel = mapRepeatLabel(r.getRepeatType());
        res.status = r.getStatus();
        res.isSent = r.getIsSent();
        res.sentAt = r.getSentAt();
        res.googleCalendarEventId = r.getGoogleCalendarEventId();
        res.synced = r.getGoogleCalendarEventId() != null && !r.getGoogleCalendarEventId().isBlank();
        return res;
    }

    private static String mapTypeLabel(String type) {
        if (type == null) return "Khác";
        return switch (type) {
            case "MEDICATION"   -> "Uống thuốc";
            case "BLOOD_SUGAR"  -> "Đo đường huyết";
            case "MEAL"         -> "Bữa ăn";
            case "EXERCISE"     -> "Tập thể dục";
            case "DOCTOR_VISIT" -> "Khám bác sĩ";
            default             -> "Tuỳ chỉnh";
        };
    }

    private static String mapTypeIcon(String type) {
        if (type == null) return "📌";
        return switch (type) {
            case "MEDICATION"   -> "💊";
            case "BLOOD_SUGAR"  -> "🩸";
            case "MEAL"         -> "🍽️";
            case "EXERCISE"     -> "🏃";
            case "DOCTOR_VISIT" -> "🏥";
            default             -> "📌";
        };
    }

    private static String mapRepeatLabel(String repeat) {
        if (repeat == null) return "Không lặp";
        return switch (repeat) {
            case "DAILY"   -> "Hàng ngày";
            case "WEEKLY"  -> "Hàng tuần";
            case "MONTHLY" -> "Hàng tháng";
            default        -> "Không lặp";
        };
    }

    // ==================== GETTERS ====================
    public Long getId() { return id; }
    public Long getPatientId() { return patientId; }
    public String getReminderType() { return reminderType; }
    public String getReminderTypeLabel() { return reminderTypeLabel; }
    public String getReminderTypeIcon() { return reminderTypeIcon; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getReminderTime() { return reminderTime; }
    public String getReminderTimeFormatted() { return reminderTimeFormatted; }
    public String getRepeatType() { return repeatType; }
    public String getRepeatTypeLabel() { return repeatTypeLabel; }
    public String getStatus() { return status; }
    public Boolean getIsSent() { return isSent; }
    public LocalDateTime getSentAt() { return sentAt; }
    public String getGoogleCalendarEventId() { return googleCalendarEventId; }
    public boolean isSynced() { return synced; }
}

