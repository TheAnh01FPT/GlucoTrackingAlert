package fpt.swp391.GlucoTrackAlert.repository.medication;

import fpt.swp391.GlucoTrackAlert.model.medication.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {
    List<Medicine> findByActiveTrueOrderByNameAsc();
}