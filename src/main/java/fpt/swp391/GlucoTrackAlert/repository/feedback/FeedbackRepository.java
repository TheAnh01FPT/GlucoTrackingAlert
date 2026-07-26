package fpt.swp391.GlucoTrackAlert.repository.feedback;

import fpt.swp391.GlucoTrackAlert.model.feedback.Feedback;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByPatientOrderByCreatedAtDesc(Patient patient);
    List<Feedback> findAllByOrderByCreatedAtDesc();
}
