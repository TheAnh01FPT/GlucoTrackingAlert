package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.Doctor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    // Kiểm tra email của user liên kết với bác sĩ
    boolean existsByUserEmail(String email);

    // Tìm Doctor theo email của user liên kết
    Optional<Doctor> findByUserEmail(String email);

    // Lấy danh sách bác sĩ theo status (vd: pending_approval, active, rejected)
    List<Doctor> findByStatus(String status);

    Optional<Doctor> findByUserId(Long userId);

    // Tìm theo số CCCD — dùng để check trùng
    Optional<Doctor> findByNationalId(String nationalId);

    // Tìm theo số chứng chỉ hành nghề — dùng để check trùng
    Optional<Doctor> findByPracticeLicense(String practiceLicense);
}