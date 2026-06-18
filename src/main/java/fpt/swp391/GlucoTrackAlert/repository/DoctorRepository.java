package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
// FIX 4: Đổi generic type từ Integer → Long
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    boolean existsByUserEmail(String email);

    Optional<Doctor> findByUserId(Long userId);
}