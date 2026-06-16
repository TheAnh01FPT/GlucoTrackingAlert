package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.DoctorRecommendation;
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
            Integer doctorId, Long patientId, String status);

    // Kiểm tra khuyến nghị có thuộc về bác sĩ này không (dùng để phân quyền)
    Optional<DoctorRecommendation> findByIdAndDoctorId(Long id, Integer doctorId);

    // Kiểm tra bác sĩ có được assign với bệnh nhân không (dùng trong service)
    boolean existsByDoctorIdAndPatientId(Integer doctorId, Long patientId);
}
