package fpt.swp391.GlucoTrackAlert.repository.relative;

import fpt.swp391.GlucoTrackAlert.model.relative.Relative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RelativeRepository extends JpaRepository<Relative, Long> {
    List<Relative> findByPatientId(Long patientId);
}
