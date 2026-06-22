package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.dto.AssignmentRequest;
import fpt.swp391.GlucoTrackAlert.dto.AssignmentResponse;
import fpt.swp391.GlucoTrackAlert.service.impl.DoctorPatientAssignmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class DoctorPatientAssignmentController {

    private final DoctorPatientAssignmentService assignmentService;

    @PostMapping
    public ResponseEntity<AssignmentResponse> assignDoctor(
            @RequestBody AssignmentRequest request
    ) {
        return ResponseEntity.ok(
                AssignmentResponse.from(assignmentService.assignDoctor(request))
        );
    }

    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getAllAssignments() {
        return ResponseEntity.ok(
                assignmentService.getAllAssignments()
                        .stream()
                        .map(AssignmentResponse::from)
                        .toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssignmentResponse> updateAssignment(
            @PathVariable Long id,
            @RequestBody AssignmentRequest request
    ) {
        return ResponseEntity.ok(
                AssignmentResponse.from(assignmentService.updateAssignment(id, request))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.ok("Assignment deleted");
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<String> hardDeleteAssignment(@PathVariable Long id) {
        assignmentService.hardDeleteAssignment(id);
        return ResponseEntity.ok("Assignment permanently deleted");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}