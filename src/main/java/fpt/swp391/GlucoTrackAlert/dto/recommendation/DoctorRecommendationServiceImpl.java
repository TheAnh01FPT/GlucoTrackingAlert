package fpt.swp391.GlucoTrackAlert.service.recommendation;

import fpt.swp391.GlucoTrackAlert.dto.recommendation.DoctorRecommendationRequest;
import fpt.swp391.GlucoTrackAlert.dto.recommendation.DoctorRecommendationResponse;
import fpt.swp391.GlucoTrackAlert.model.Doctor;
import fpt.swp391.GlucoTrackAlert.model.DoctorRecommendation;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRecommendationRepository;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorRecommendationServiceImpl implements DoctorRecommendationService {

    private final DoctorRecommendationRepository recommendationRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorPatientAssignmentRepository assignmentRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public DoctorRecommendationResponse create(String doctorEmail, DoctorRecommendationRequest request) {
        Doctor doctor = getDoctorByEmail(doctorEmail);
        Patient patient = getPatient(request.getPatientId());
        validateAssignment(doctor.getId(), patient.getId());

        DoctorRecommendation rec = new DoctorRecommendation();
        rec.setDoctor(doctor);
        rec.setPatient(patient);
        rec.setTitle(request.getTitle());
        rec.setRecommendation(request.getRecommendation());
        rec.setStatus("active");

        DoctorRecommendation saved = recommendationRepository.save(rec);

        notificationService.createNotification(
            patient.getUser().getId(),
            "Khuyến nghị mới từ bác sĩ",
            "BS. " + doctor.getFullName() + " vừa gửi khuyến nghị mới: " + request.getTitle(),
            "RECOMMENDATION_CREATED"
        );

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorRecommendationResponse> getByDoctorAndPatient(String doctorEmail, Long patientId) {
        Doctor doctor = getDoctorByEmail(doctorEmail);
        validateAssignment(doctor.getId(), patientId);
        return recommendationRepository
                .findByDoctorIdAndPatientIdAndStatusOrderByCreatedAtDesc(doctor.getId(), patientId, "active")
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorRecommendationResponse> getByPatient(Long patientId) {
        return recommendationRepository
                .findByPatientIdAndStatusOrderByCreatedAtDesc(patientId, "active")
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DoctorRecommendationResponse update(String doctorEmail, Long recommendationId, DoctorRecommendationRequest request) {
        Doctor doctor = getDoctorByEmail(doctorEmail);

        DoctorRecommendation rec = recommendationRepository
                .findByIdAndDoctorId(recommendationId, doctor.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khuyến nghị hoặc bạn không có quyền chỉnh sửa"));

        if ("inactive".equals(rec.getStatus())) {
            throw new RuntimeException("Không thể chỉnh sửa khuyến nghị đã bị xóa");
        }

        String oldTitle = rec.getTitle();
        rec.setTitle(request.getTitle());
        rec.setRecommendation(request.getRecommendation());

        DoctorRecommendation saved = recommendationRepository.save(rec);

        notificationService.createNotification(
            rec.getPatient().getUser().getId(),
            "Khuyến nghị được cập nhật",
            "BS. " + doctor.getFullName() + " vừa cập nhật khuyến nghị: \"" + oldTitle + "\"",
            "RECOMMENDATION_UPDATED"
        );

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String doctorEmail, Long recommendationId) {
        Doctor doctor = getDoctorByEmail(doctorEmail);

        DoctorRecommendation rec = recommendationRepository
                .findByIdAndDoctorId(recommendationId, doctor.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khuyến nghị hoặc bạn không có quyền xóa"));

        rec.setStatus("inactive");
        recommendationRepository.save(rec);
    }

    // ========== PRIVATE HELPERS ==========

    private Doctor getDoctorByEmail(String email) {
        Doctor doctor = doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ"));

        // Chỉ bác sĩ active mới được thao tác
        if (!"active".equals(doctor.getStatus())) {
            throw new RuntimeException("Tài khoản bác sĩ chưa được kích hoạt. Vui lòng hoàn tất xác minh hồ sơ.");
        }

        return doctor;
    }

    private Patient getPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân"));
    }

    private void validateAssignment(Integer doctorId, Long patientId) {
        boolean isAssigned = assignmentRepository
                .findByDoctorIdAndPatientId(doctorId, patientId)
                .map(a -> "active".equals(a.getStatus()))
                .orElse(false);

        if (!isAssigned) {
            throw new RuntimeException("Bác sĩ không có quyền thao tác với bệnh nhân này");
        }
    }

    private DoctorRecommendationResponse toResponse(DoctorRecommendation rec) {
        return DoctorRecommendationResponse.builder()
                .id(rec.getId())
                .title(rec.getTitle())
                .recommendation(rec.getRecommendation())
                .status(rec.getStatus())
                .doctorId(rec.getDoctor().getId().longValue())
                .doctorName(rec.getDoctor().getFullName())
                .patientId(rec.getPatient().getId())
                .patientName(rec.getPatient().getFullName())
                .createdAt(rec.getCreatedAt())
                .updatedAt(rec.getUpdatedAt())
                .build();
    }
}