package fpt.swp391.GlucoTrackAlert.service.recommendation;

import fpt.swp391.GlucoTrackAlert.dto.recommendation.DoctorRecommendationRequest;
import fpt.swp391.GlucoTrackAlert.dto.recommendation.DoctorRecommendationResponse;

import java.util.List;

public interface DoctorRecommendationService {

    // Bác sĩ tạo khuyến nghị cho bệnh nhân được assign
    DoctorRecommendationResponse create(String doctorEmail, DoctorRecommendationRequest request);

    // Bác sĩ xem danh sách khuyến nghị mình đã tạo cho bệnh nhân
    List<DoctorRecommendationResponse> getByDoctorAndPatient(String doctorEmail, Long patientId);

    // Bệnh nhân xem danh sách khuyến nghị của mình
    List<DoctorRecommendationResponse> getByPatient(Long patientId);

    // Bác sĩ sửa khuyến nghị (chỉ được sửa của mình)
    DoctorRecommendationResponse update(String doctorEmail, Long recommendationId, DoctorRecommendationRequest request);

    // Bác sĩ xóa mềm khuyến nghị (chỉ được xóa của mình)
    void delete(String doctorEmail, Long recommendationId);
}