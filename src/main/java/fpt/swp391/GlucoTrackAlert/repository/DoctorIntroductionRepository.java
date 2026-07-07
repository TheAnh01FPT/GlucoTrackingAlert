package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.DoctorIntroduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DoctorIntroductionRepository extends JpaRepository<DoctorIntroduction, Long> {

    List<DoctorIntroduction> findByStatusOrderByDisplayOrderAsc(String status);
}