package fpt.swp391.GlucoTrackAlert.model;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "doctor_patient_assignments")
@Getter
@Setter
public class DoctorPatientAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // FIX 4: Đổi Integer → Long để nhất quán với các entity khác
    private Long id;

    @ManyToOne
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private Patient patient;

    private LocalDateTime assignedAt;

    private String status;

    private String note;
}