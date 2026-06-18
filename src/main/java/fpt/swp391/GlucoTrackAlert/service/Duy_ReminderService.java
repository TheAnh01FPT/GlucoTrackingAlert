package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.dto.reminder.Duy_ReminderRequest;
import fpt.swp391.GlucoTrackAlert.dto.reminder.Duy_ReminderResponse;

import java.util.List;
import java.util.Optional;

public interface Duy_ReminderService {

    // CRUD cơ bản
    Duy_ReminderResponse create(Duy_ReminderRequest request);

    List<Duy_ReminderResponse> getAllByPatient(Long patientId);

    Optional<Duy_ReminderResponse> getById(Long id);

    Duy_ReminderResponse update(Long id, Duy_ReminderRequest request);

    void delete(Long id);

    // Huỷ tất cả reminder được sinh ra từ một PrescriptionItem (dùng khi đơn thuốc bị CANCELLED)
    void cancelByPrescriptionItemId(Long prescriptionItemId);

    // Lọc
    List<Duy_ReminderResponse> getByPatientAndStatus(Long patientId, String status);

    List<Duy_ReminderResponse> getByPatientAndType(Long patientId, String type);

    List<Duy_ReminderResponse> getUpcoming(Long patientId);

    // Hành động nhanh (giống iPhone)
    Duy_ReminderResponse markComplete(Long id);

    Duy_ReminderResponse markActive(Long id);

    // Thống kê
    long countActive(Long patientId);

    // Google Calendar sync
    Duy_ReminderResponse syncToGoogleCalendar(Long reminderId, String googleAccessToken);

    void deleteFromGoogleCalendar(Long reminderId, String googleAccessToken);
}