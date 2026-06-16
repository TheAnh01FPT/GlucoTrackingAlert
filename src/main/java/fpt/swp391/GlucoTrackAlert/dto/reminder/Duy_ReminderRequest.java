package fpt.swp391.GlucoTrackAlert.dto.reminder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class Duy_ReminderRequest {

    @NotNull(message = "patientId không được để trống")
    private Long patientId;

    @NotBlank(message = "reminderType không được để trống")
    private String reminderType; // MEDICATION, BLOOD_SUGAR, MEAL, EXERCISE, DOCTOR_VISIT, CUSTOM

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String message;

    @NotNull(message = "Thời gian nhắc không được để trống")
    private String reminderTime; // ISO string: "2025-06-15T08:00:00"

    private String repeatType = "NONE"; // NONE, DAILY, WEEKLY, MONTHLY

    // Token OAuth2 Google Calendar của user (frontend gửi lên khi muốn sync GG)
    private String googleAccessToken;

    // ==================== GETTERS & SETTERS ====================
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getReminderTime() { return reminderTime; }
    public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }

    public String getRepeatType() { return repeatType; }
    public void setRepeatType(String repeatType) { this.repeatType = repeatType; }

    public String getGoogleAccessToken() { return googleAccessToken; }
    public void setGoogleAccessToken(String googleAccessToken) { this.googleAccessToken = googleAccessToken; }
}
