package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.service.impl.DoctorPatientAssignmentService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorPatientController {

    private final DoctorPatientAssignmentService assignmentService;

    @GetMapping("/{doctorId}/patients")
    public ResponseEntity<List<Map<String, Object>>> getPatientsByDoctor(
            @PathVariable Integer doctorId
    ) {
        return ResponseEntity.ok(assignmentService.getPatientsByDoctor(doctorId));
    }
}