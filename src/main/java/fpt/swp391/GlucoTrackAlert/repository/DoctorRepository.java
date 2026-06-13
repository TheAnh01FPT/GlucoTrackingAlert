package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.Doctor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Integer> {
    // Kiểm tra email của user liên kết với bác sĩ — dùng để check đã tạo hồ sơ chưa
    boolean existsByUserEmail(String email);

    // Tìm Doctor theo email của user liên kết
    Optional<Doctor> findByUserEmail(String email);

    // Lấy danh sách bác sĩ theo status (vd: pending_approval, active, rejected)
    java.util.List<Doctor> findByStatus(String status);
}