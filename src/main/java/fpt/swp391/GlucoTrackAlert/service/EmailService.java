package fpt.swp391.GlucoTrackAlert.service;

public interface EmailService {
    void sendSimpleMessage(String to, String subject, String text);
}

