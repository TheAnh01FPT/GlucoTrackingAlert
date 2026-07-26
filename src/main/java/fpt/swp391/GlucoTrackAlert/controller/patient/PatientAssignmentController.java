package fpt.swp391.GlucoTrackAlert.controller.patient;

import fpt.swp391.GlucoTrackAlert.dto.doctor.AssignmentResponse;
import fpt.swp391.GlucoTrackAlert.dto.doctor.PublicDoctorResponse;
import fpt.swp391.GlucoTrackAlert.dto.doctor.DoctorResponse;
import fpt.swp391.GlucoTrackAlert.dto.ProposeAssignmentRequest;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.doctor.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.doctor.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.impl.doctor.DoctorPatientAssignmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Bệnh nhân đề xuất bác sĩ đồng hành / hủy đề xuất đang chờ duyệt.
 * Việc duyệt/từ chối do Admin thực hiện (xem DoctorPatientAssignmentController).
 */
@RestController
@RequestMapping("/api/patient/assignments")
@RequiredArgsConstructor
public class PatientAssignmentController {

    private final DoctorPatientAssignmentService assignmentService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorPatientAssignmentRepository assignmentRepository;

    private Patient getLoggedInPatient() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản chưa đăng nhập hoặc không tồn tại"));
        return patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh nhân"));
    }

    /**
     * [PATIENT] Lấy danh sách bác sĩ đang hoạt động để bệnh nhân chọn đề xuất ghép.
     * Bác sĩ đã đủ số lượng bệnh nhân tối đa (MAX_PATIENTS_PER_DOCTOR) sẽ bị lọc bỏ
     * ngay tại đây, không hiển thị cho bệnh nhân chọn nữa — tránh việc bệnh nhân
     * tạo đề xuất cho một bác sĩ chắc chắn sẽ bị admin từ chối vì hết chỗ.
     */
    @GetMapping("/doctors")
    public ResponseEntity<List<PublicDoctorResponse>> getActiveDoctors() {
        return ResponseEntity.ok(
                doctorRepository.findByStatus("active")
                        .stream()
                        .filter(d -> assignmentRepository.countByDoctorIdAndStatus(
                                d.getId(), "active") < DoctorPatientAssignmentService.MAX_PATIENTS_PER_DOCTOR)
                        .map(PublicDoctorResponse::from)
                        .toList()
        );
    }

    /**
     * [PATIENT] Lấy lịch sử đề xuất / phân công bác sĩ của chính bệnh nhân đang đăng nhập.
     */
    @GetMapping("/mine")
    public ResponseEntity<List<AssignmentResponse>> getMyAssignments() {
        Patient patient = getLoggedInPatient();
        return ResponseEntity.ok(
                assignmentRepository.findByPatientId(patient.getId()).stream()
                        .map(AssignmentResponse::from)
                        .toList()
        );
    }

    @PostMapping("/propose")
    public ResponseEntity<AssignmentResponse> proposeAssignment(
            @RequestBody ProposeAssignmentRequest request
    ) {
        Patient patient = getLoggedInPatient();
        return ResponseEntity.ok(
                AssignmentResponse.from(
                        assignmentService.proposeAssignment(
                                patient.getId(), request.getDoctorId(), request.getNote())
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> cancelPendingAssignment(@PathVariable Long id) {
        Patient patient = getLoggedInPatient();
        assignmentService.cancelPendingAssignment(patient.getId(), id);
        return ResponseEntity.ok("Đã hủy đề xuất");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}