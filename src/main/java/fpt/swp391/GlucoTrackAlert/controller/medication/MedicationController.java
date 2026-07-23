package fpt.swp391.GlucoTrackAlert.controller.medication;

import fpt.swp391.GlucoTrackAlert.dto.medication.MedicationLogResponse;
import fpt.swp391.GlucoTrackAlert.dto.medication.MedicineResponse;
import fpt.swp391.GlucoTrackAlert.dto.medication.PrescriptionRequest;
import fpt.swp391.GlucoTrackAlert.dto.medication.PrescriptionResponse;
import fpt.swp391.GlucoTrackAlert.model.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.doctor.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.medication.MedicationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {

    @Autowired
    private MedicationService medicationService;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Lấy doctorId của bác sĩ ĐANG ĐĂNG NHẬP từ JWT/SecurityContext, KHÔNG tin
     * vào doctorId mà client gửi trong body request (tránh giả mạo người kê đơn).
     */
    private Long getCurrentDoctorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalArgumentException("Không xác định được bác sĩ đang đăng nhập");
        }
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng: " + email));
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản hiện tại không phải bác sĩ"));
        return doctor.getId();
    }

    @PostMapping("/prescriptions")
    public ResponseEntity<?> createPrescription(@Valid @RequestBody PrescriptionRequest request) {
        try {
            // Ghi đè doctorId bằng bác sĩ đang đăng nhập, bỏ qua giá trị client gửi lên
            request.setDoctorId(getCurrentDoctorId());
            return ResponseEntity.ok(medicationService.createPrescription(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/prescriptions/{id}/cancel")
    public ResponseEntity<?> cancelPrescription(@PathVariable Long id) {
        try {
            medicationService.cancelPrescription(id);
            return ResponseEntity.ok(Map.of("message", "Đã huỷ đơn thuốc ID: " + id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/prescriptions/patient/{patientId}")
    public ResponseEntity<List<PrescriptionResponse>> getPrescriptions(@PathVariable Long patientId) {
        return ResponseEntity.ok(medicationService.getPrescriptionsByPatient(patientId));
    }

    @GetMapping("/logs/patient/{patientId}")
    public ResponseEntity<List<MedicationLogResponse>> getDailyLogs(
            @PathVariable Long patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(medicationService.getDailyLogs(patientId, date));
    }

    @PatchMapping("/logs/{logId}/taken")
    public ResponseEntity<?> markTaken(@PathVariable Long logId) {
        try {
            return ResponseEntity.ok(medicationService.markTaken(logId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<MedicineResponse>> getMedicineCatalog() {
        return ResponseEntity.ok(medicationService.getMedicineCatalog());
    }

    @GetMapping("/adherence/patient/{patientId}")
    public ResponseEntity<Map<String, Object>> getAdherence(@PathVariable Long patientId) {
        return ResponseEntity.ok(medicationService.getAdherenceStat(patientId));
    }
}