package fpt.swp391.GlucoTrackAlert.repository.doctor;

import fpt.swp391.GlucoTrackAlert.model.doctor.DoctorIntroduction;
import fpt.swp391.GlucoTrackAlert.model.doctor.DoctorIntroduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DoctorIntroductionRepository extends JpaRepository<DoctorIntroduction, Long> {

    List<DoctorIntroduction> findByStatusOrderByDisplayOrderAsc(String status);

    // Chặn 1 bác sĩ được giới thiệu trùng nhiều lần
    boolean existsByDoctorId(Long doctorId);

    boolean existsByDoctorIdAndIdNot(Long doctorId, Long id);

    // Chặn trùng thứ tự hiển thị (displayOrder) giữa các bác sĩ
    boolean existsByDisplayOrder(Integer displayOrder);

    boolean existsByDisplayOrderAndIdNot(Integer displayOrder, Long id);
}