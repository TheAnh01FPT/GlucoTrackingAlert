package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.model.Patient;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ==========================================
// 13. DOCTOR XEM DANH SÁCH BỆNH NHÂN
// DoctorPatientController.java
// ==========================================

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorPatientController {

    private final DoctorPatientAssignmentRepository assignmentRepository;

    @GetMapping("/{doctorId}/patients")
    public ResponseEntity<List<Patient>> getPatientsByDoctor(
            @PathVariable Integer doctorId
    ) {

        List<DoctorPatientAssignment> assignments =
                assignmentRepository.findByDoctorIdAndStatus(
                        doctorId,
                        "active"
                );

        List<Patient> patients = assignments.stream()
                .map(DoctorPatientAssignment::getPatient)
                .toList();

        return ResponseEntity.ok(patients);
    }
}
