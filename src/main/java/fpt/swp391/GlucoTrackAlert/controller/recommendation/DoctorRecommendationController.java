package fpt.swp391.GlucoTrackAlert.controller.recommendation;

import fpt.swp391.GlucoTrackAlert.dto.recommendation.DoctorRecommendationRequest;
import fpt.swp391.GlucoTrackAlert.dto.recommendation.DoctorRecommendationResponse;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.recommendation.DoctorRecommendationService;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class DoctorRecommendationController {

    private final DoctorRecommendationService recommendationService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;

    // Bác sĩ tạo khuyến nghị
    @PostMapping
    public ResponseEntity<DoctorRecommendationResponse> create(
            Principal principal,
            @Valid @RequestBody DoctorRecommendationRequest request) {
        return ResponseEntity.ok(recommendationService.create(principal.getName(), request));
    }

    // Bác sĩ xem khuyến nghị mình đã tạo cho bệnh nhân
    @GetMapping("/doctor/patient/{patientId}")
    public ResponseEntity<List<DoctorRecommendationResponse>> getByDoctorAndPatient(
            Principal principal,
            @PathVariable Long patientId) {
        return ResponseEntity.ok(recommendationService.getByDoctorAndPatient(principal.getName(), patientId));
    }

    // Bệnh nhân xem khuyến nghị của mình
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<DoctorRecommendationResponse>> getByPatient(
            @PathVariable Long patientId) {
        if (!isOwnPatientIdOrAdmin(patientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(recommendationService.getByPatient(patientId));
    }

    // Bác sĩ sửa khuyến nghị của mình
    @PutMapping("/{id}")
    public ResponseEntity<DoctorRecommendationResponse> update(
            Principal principal,
            @PathVariable Long id,
            @Valid @RequestBody DoctorRecommendationRequest request) {
        return ResponseEntity.ok(recommendationService.update(principal.getName(), id, request));
    }

    // Bác sĩ xóa mềm khuyến nghị của mình
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Principal principal,
            @PathVariable Long id) {
        recommendationService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    // Bác sĩ xem tất cả kể cả đã xóa (includeInactive=true)
    @GetMapping("/doctor/patient/{patientId}/all")
    public ResponseEntity<List<DoctorRecommendationResponse>> getAllByDoctorAndPatient(
            Principal principal,
            @PathVariable Long patientId) {
        return ResponseEntity.ok(recommendationService.getAllByDoctorAndPatient(principal.getName(), patientId));
    }

    // Bệnh nhân xem tất cả kể cả đã xóa
    @GetMapping("/patient/{patientId}/all")
    public ResponseEntity<List<DoctorRecommendationResponse>> getAllByPatient(
            @PathVariable Long patientId) {
        if (!isOwnPatientIdOrAdmin(patientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(recommendationService.getAllByPatient(patientId));
    }

    // Bệnh nhân đánh dấu đã đọc khuyến nghị
    @PatchMapping("/patient/{patientId}/{id}/read")
    public ResponseEntity<DoctorRecommendationResponse> markAsRead(
            @PathVariable Long patientId,
            @PathVariable Long id) {
        if (!isOwnPatientIdOrAdmin(patientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(recommendationService.markAsRead(patientId, id));
    }

    // ========== PRIVATE HELPERS ==========

    /**
     * Chặn IDOR: bệnh nhân chỉ được thao tác trên patientId của chính mình.
     * Chỉ Admin được bypass. Doctor KHÔNG được bypass ở đây — Doctor phải đi qua
     * route riêng /doctor/patient/{patientId} (đã check assignment ở service).
     */
    private boolean isOwnPatientIdOrAdmin(Long patientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return false;
        }
        Patient patient = patientRepository.findByUserId(user.getId()).orElse(null);
        return patient != null && patient.getId().equals(patientId);
    }
}