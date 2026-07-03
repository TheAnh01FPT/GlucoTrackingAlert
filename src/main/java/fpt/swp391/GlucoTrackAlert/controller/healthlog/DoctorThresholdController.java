package fpt.swp391.GlucoTrackAlert.controller.healthlog;

import fpt.swp391.GlucoTrackAlert.model.HealthThreshold;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.HealthThresholdService;
import fpt.swp391.GlucoTrackAlert.enums.MetricType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/health-logs/doctor-view/thresholds")
public class DoctorThresholdController {

    @Autowired
    private HealthThresholdService healthThresholdService;

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private fpt.swp391.GlucoTrackAlert.repository.user.UserRepository userRepository;
    @Autowired
    private fpt.swp391.GlucoTrackAlert.repository.DoctorRepository doctorRepository;
    @Autowired
    private fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository assignmentRepository;

    private Long getCurrentUserId() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        String email = auth.getName();
        fpt.swp391.GlucoTrackAlert.model.user.User user = userRepository.findByEmail(email).orElse(null);
        return user != null ? user.getId() : null;
    }

    private boolean hasRole(String role) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    private boolean isDoctorAssignedToPatient(Long patientId) {
        if (hasRole("ROLE_ADMIN")) return true;
        if (!hasRole("ROLE_DOCTOR")) return false;
        Long currentUserId = getCurrentUserId();
        fpt.swp391.GlucoTrackAlert.model.Doctor doctor = doctorRepository.findByUserId(currentUserId).orElse(null);
        if (doctor == null) return false;
        return assignmentRepository.findByDoctorIdAndPatientId(doctor.getId(), patientId).isPresent();
    }

    // Xem ngưỡng của 1 bệnh nhân
    @GetMapping
    public String viewThresholds(@RequestParam Long patientId, Model model, RedirectAttributes redirectAttributes) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) return "redirect:/health-logs/doctor-view";

        if (!hasRole("ROLE_ADMIN")) {
            if (!isDoctorAssignedToPatient(patientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không được phân công cho bệnh nhân này");
                return "redirect:/health-logs/doctor-view";
            }
        }

        List<HealthThreshold> patientThresholds = healthThresholdService.findByPatientId(patientId);
        List<HealthThreshold> allDefaults = healthThresholdService.findDefaults();

        Map<MetricType, HealthThreshold> customMap = new HashMap<>();
        if (patientThresholds != null) {
            for (HealthThreshold t : patientThresholds) {
                customMap.put(t.getMetricType(), t);
            }
        }

        Map<MetricType, HealthThreshold> defaultMap = new HashMap<>();
        String patientType = patient.getPatientType();
        String targetType = (patientType != null && !patientType.isBlank()) ? patientType : "adult";
        if (allDefaults != null) {
            for (HealthThreshold t : allDefaults) {
                if (targetType.equalsIgnoreCase(t.getPatientType())) {
                    defaultMap.put(t.getMetricType(), t);
                }
            }
        }

        model.addAttribute("patient", patient);
        model.addAttribute("customThresholds", customMap);
        model.addAttribute("defaultThresholds", defaultMap);
        model.addAttribute("metricTypes", java.util.Arrays.asList(MetricType.values()));
        return "healthlog/doctor-thresholds";
    }

    // Bác sĩ lưu ngưỡng riêng cho bệnh nhân
    @PostMapping("/save")
    public String saveThreshold(@RequestParam Long patientId,
            @RequestParam String metricType,
            @RequestParam BigDecimal normalMin,
            @RequestParam BigDecimal normalMax,
            @RequestParam BigDecimal warningMin,
            @RequestParam BigDecimal warningMax,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {
        try {
            if (!hasRole("ROLE_ADMIN")) {
                if (!isDoctorAssignedToPatient(patientId)) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Bạn không được phân công cho bệnh nhân này");
                    return "redirect:/health-logs/doctor-view/thresholds?patientId=" + patientId;
                }
            }
            Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân"));

            MetricType mt = MetricType.from(metricType);
            if (mt == null) throw new IllegalArgumentException("metricType không hợp lệ: " + metricType);

            healthThresholdService.savePatientThreshold(
                patientId, mt, normalMin, normalMax,
                warningMin, warningMax, description, patient);

            redirectAttributes.addFlashAttribute("successMessage",
                "Đã cập nhật ngưỡng " + mt.name() + " cho bệnh nhân");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/health-logs/doctor-view/thresholds?patientId=" + patientId;
    }

    // Xóa ngưỡng riêng — reset về mặc định
    @PostMapping("/reset")
    public String resetThreshold(@RequestParam Long patientId,
            @RequestParam String metricType,
            RedirectAttributes redirectAttributes) {
        MetricType mt = MetricType.from(metricType);
        if (mt == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "metricType không hợp lệ: " + metricType);
            return "redirect:/health-logs/doctor-view/thresholds?patientId=" + patientId;
        }
        if (!hasRole("ROLE_ADMIN")) {
            if (!isDoctorAssignedToPatient(patientId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn không được phân công cho bệnh nhân này");
                return "redirect:/health-logs/doctor-view/thresholds?patientId=" + patientId;
            }
        }
        healthThresholdService.deletePatientThreshold(patientId, mt);
        redirectAttributes.addFlashAttribute("successMessage",
            "Đã reset về ngưỡng mặc định");
        return "redirect:/health-logs/doctor-view/thresholds?patientId=" + patientId;
    }
}
