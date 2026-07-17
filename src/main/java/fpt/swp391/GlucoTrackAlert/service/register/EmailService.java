package fpt.swp391.GlucoTrackAlert.service.register;

public interface EmailService {
    void sendSimpleMessage(String to, String subject, String text);
    void sendHtmlMessage(String to, String subject, String htmlContent);
    void sendSimpleMessageAsync(String to, String subject, String text);
    void sendHtmlMessageAsync(String to, String subject, String htmlContent);
}

