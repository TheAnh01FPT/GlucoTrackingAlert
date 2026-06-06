package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
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

// ==========================================
// 12. CRUD PHÂN CÔNG BÁC SĨ
// DoctorPatientAssignmentController.java
// ==========================================

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class DoctorPatientAssignmentController {

    private final DoctorPatientAssignmentService assignmentService;

    // CREATE ASSIGNMENT
    @PostMapping
    public ResponseEntity<DoctorPatientAssignment> assignDoctor(
            @RequestBody DoctorPatientAssignment assignment
    ) {
        return ResponseEntity.ok(
                assignmentService.assignDoctor(assignment)
        );
    }

    // GET ALL ASSIGNMENTS
    @GetMapping
    public ResponseEntity<List<DoctorPatientAssignment>> getAllAssignments() {
        return ResponseEntity.ok(
                assignmentService.getAllAssignments()
        );
    }

    // UPDATE ASSIGNMENT
    @PutMapping("/{id}")
    public ResponseEntity<DoctorPatientAssignment> updateAssignment(
            @PathVariable Integer id,
            @RequestBody DoctorPatientAssignment assignment
    ) {
        return ResponseEntity.ok(
                assignmentService.updateAssignment(id, assignment)
        );
    }

    // DELETE ASSIGNMENT (soft - chuyển inactive)
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAssignment(@PathVariable Integer id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.ok("Assignment deleted");
    }

    // HARD DELETE (xóa hẳn khỏi DB - chỉ cho phép với record đã inactive)
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<String> hardDeleteAssignment(@PathVariable Integer id) {
        assignmentService.hardDeleteAssignment(id);
        return ResponseEntity.ok("Assignment permanently deleted");
    }

    // Trả về lỗi dạng plain text thay vì redirect sang /error HTML page
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}