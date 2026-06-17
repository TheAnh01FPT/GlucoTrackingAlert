package fpt.swp391.GlucoTrackAlert.controller.healthlog;

import fpt.swp391.GlucoTrackAlert.model.Doctor;
import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskAssessment;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskWarning;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskAssessmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskWarningRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/health-logs")
@RequiredArgsConstructor
public class RiskWarningController {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorPatientAssignmentRepository assignmentRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskWarningRepository riskWarningRepository;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        User user = userRepository.findByEmail(auth.getName()).orElse(null);
        return user != null ? user.getId() : null;
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    private Long resolvePatientId(Long userId) {
        if (userId == null) return null;
        return patientRepository.findByUserId(userId).map(Patient::getId).orElse(null);
    }

    private boolean isDoctorAssignedToPatient(Long patientId) {
        if (hasRole("ROLE_ADMIN")) return true;
        if (!hasRole("ROLE_DOCTOR")) return false;
        Long currentUserId = getCurrentUserId();
        Doctor doctor = doctorRepository.findByUserId(currentUserId).orElse(null);
        if (doctor == null) return false;
        return assignmentRepository.findByDoctorIdAndPatientId(doctor.getId(), patientId).isPresent();
    }

    @GetMapping("/risk-warnings")
    public String getPatientRiskWarnings(@RequestParam Long userId, Model model) {
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            Long curUserId = getCurrentUserId();
            if (curUserId == null || !curUserId.equals(userId)) {
                return "redirect:/login";
            }
        }

        Long patientId = resolvePatientId(userId);
        if (patientId == null) return "redirect:/login";

        RiskAssessment latest = riskAssessmentRepository
            .findTopByPatient_IdAndAssessmentTypeOrderByAssessedAtDesc(patientId, "NEPHROPATHY")
            .orElse(null);

        List<RiskWarning> warnings = riskWarningRepository
            .findByPatient_IdOrderByCreatedAtDesc(patientId);

        model.addAttribute("latestAssessment", latest);
        model.addAttribute("warnings", warnings);
        model.addAttribute("userId", userId);
        return "healthlog/risk-warning";
    }

    @GetMapping("/doctor/risk-warnings")
    public String getDoctorRiskWarnings(@RequestParam Long userId,
                                        @RequestParam Long patientId,
                                        Model model) {
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            return "redirect:/login";
        }
        if (hasRole("ROLE_DOCTOR") && !isDoctorAssignedToPatient(patientId)) {
            return "redirect:/health-logs/doctor-view";
        }

        RiskAssessment latest = riskAssessmentRepository
            .findTopByPatient_IdAndAssessmentTypeOrderByAssessedAtDesc(patientId, "NEPHROPATHY")
            .orElse(null);

        List<RiskWarning> warnings = riskWarningRepository
            .findByPatient_IdOrderByCreatedAtDesc(patientId);

        Patient patient = patientRepository.findById(patientId).orElse(null);

        model.addAttribute("latestAssessment", latest);
        model.addAttribute("warnings", warnings);
        model.addAttribute("userId", userId);
        model.addAttribute("patientId", patientId);
        model.addAttribute("patient", patient);
        return "healthlog/doctor-risk-warning";
    }

    @PostMapping("/risk-warnings/{id}/acknowledge")
    public String acknowledge(@PathVariable Long id,
                              @RequestParam Long userId,
                              RedirectAttributes redirectAttributes) {
        RiskWarning warning = riskWarningRepository.findById(id).orElse(null);
        if (warning != null) {
            warning.setStatus("acknowledged");
            warning.setAcknowledgedAt(LocalDateTime.now());
            riskWarningRepository.save(warning);
        }
        return "redirect:/health-logs/risk-warnings?userId=" + userId;
    }
}