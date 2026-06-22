package fpt.swp391.GlucoTrackAlert.controller.recommendation;

import fpt.swp391.GlucoTrackAlert.dto.recommendation.DoctorRecommendationRequest;
import fpt.swp391.GlucoTrackAlert.dto.recommendation.DoctorRecommendationResponse;
import fpt.swp391.GlucoTrackAlert.service.recommendation.DoctorRecommendationService;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class DoctorRecommendationController {

    private final DoctorRecommendationService recommendationService;

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
        return ResponseEntity.ok(recommendationService.getAllByPatient(patientId));
    }
}