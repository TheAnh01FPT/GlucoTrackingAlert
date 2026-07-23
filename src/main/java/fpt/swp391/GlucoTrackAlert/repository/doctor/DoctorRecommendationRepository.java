package fpt.swp391.GlucoTrackAlert.repository.doctor;

import fpt.swp391.GlucoTrackAlert.model.doctor.DoctorRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRecommendationRepository extends JpaRepository<DoctorRecommendation, Long> {

    // Lấy tất cả khuyến nghị đang active của bệnh nhân (bệnh nhân xem)
    List<DoctorRecommendation> findByPatientIdAndStatusOrderByCreatedAtDesc(Long patientId, String status);

    // Lấy tất cả khuyến nghị bác sĩ đã tạo (bác sĩ quản lý của mình)
    List<DoctorRecommendation> findByDoctorIdAndPatientIdAndStatusOrderByCreatedAtDesc(
            Long doctorId, Long patientId, String status);

    // Kiểm tra khuyến nghị có thuộc về bác sĩ này không (dùng để phân quyền)
    Optional<DoctorRecommendation> findByIdAndDoctorId(Long id, Long doctorId);

    // Kiểm tra bác sĩ có được assign với bệnh nhân không (dùng trong service)
    boolean existsByDoctorIdAndPatientId(Long doctorId, Long patientId);

    // Lấy tất cả kể cả inactive (bác sĩ xem lịch sử đã xóa)
    List<DoctorRecommendation> findByDoctorIdAndPatientIdOrderByCreatedAtDesc(
            Long doctorId, Long patientId);

    // Bệnh nhân xem tất cả kể cả inactive
    List<DoctorRecommendation> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    // Kiểm tra khuyến nghị có thuộc về bệnh nhân này không (dùng để đánh dấu đã đọc)
    Optional<DoctorRecommendation> findByIdAndPatientId(Long id, Long patientId);
}