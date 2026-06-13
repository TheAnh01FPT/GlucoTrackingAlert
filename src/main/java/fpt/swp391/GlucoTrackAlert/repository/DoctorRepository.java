package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Integer> {
    // Kiểm tra email của user liên kết với bác sĩ — dùng để check đã tạo hồ sơ chưa
    boolean existsByUserEmail(String email);

    Optional<Doctor> findByUserId(Long userId);
}