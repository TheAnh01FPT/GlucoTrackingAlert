package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// ==========================================
// DoctorPatientAssignmentRepository.java
// ==========================================
@Repository
public interface DoctorPatientAssignmentRepository
        extends JpaRepository<DoctorPatientAssignment, Integer> {

    List<DoctorPatientAssignment> findByDoctorIdAndStatus(Integer doctorId, String status);

    // Lấy toàn bộ assignment của 1 bác sĩ (dùng khi hard delete)
    List<DoctorPatientAssignment> findByDoctorId(Integer doctorId);

    // Kiểm tra bệnh nhân đã được phân công active chưa
    boolean existsByPatientIdAndStatus(Integer patientId, String status);

    // Lấy danh sách active theo bệnh nhân (để lọc bỏ chính record đang edit)
    List<DoctorPatientAssignment> findByPatientIdAndStatus(Integer patientId, String status);

    // Tìm record cũ của cặp bác sĩ - bệnh nhân (để reactivate thay vì insert mới)
    java.util.Optional<DoctorPatientAssignment> findByDoctorIdAndPatientId(Integer doctorId, Integer patientId);

    // Đếm số bệnh nhân active của 1 bác sĩ (giới hạn tối đa 5)
    long countByDoctorIdAndStatus(Integer doctorId, String status);
}
