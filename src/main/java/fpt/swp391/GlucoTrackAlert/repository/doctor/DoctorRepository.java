package fpt.swp391.GlucoTrackAlert.repository.doctor;

import fpt.swp391.GlucoTrackAlert.model.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.model.doctor.Doctor;
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

    // Lấy bác sĩ có status = statusParam HOẶC đang có bản xác minh staging chờ duyệt
    // (bác sĩ active gửi lại CCCD/chứng chỉ mới — xem uploadVerificationImages)
    List<Doctor> findByStatusOrPendingVerificationJsonIsNotNull(String status);

    Optional<Doctor> findByUserId(Long userId);

    // Tìm theo số CCCD — dùng để check trùng
    Optional<Doctor> findByNationalId(String nationalId);

    // Tìm theo số chứng chỉ hành nghề — dùng để check trùng
    Optional<Doctor> findByPracticeLicense(String practiceLicense);
    
    //Tìm theo số điện thoại - dùng để check trùng
    Optional<Doctor> findByPhone(String phone);
}