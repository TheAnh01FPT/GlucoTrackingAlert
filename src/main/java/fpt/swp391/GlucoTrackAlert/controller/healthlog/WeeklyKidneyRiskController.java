package fpt.swp391.GlucoTrackAlert.controller.healthlog;

import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.risk.WeeklyHealthReport;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import fpt.swp391.GlucoTrackAlert.repository.risk.WeeklyHealthReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/health-logs/kidney-risk")
@RequiredArgsConstructor
public class WeeklyKidneyRiskController {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final WeeklyHealthReportRepository weeklyHealthReportRepository;
    private final DoctorPatientAssignmentRepository assignmentRepository;
    private final fpt.swp391.GlucoTrackAlert.repository.DoctorRepository doctorRepository;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        User user = userRepository.findByEmail(auth.getName()).orElse(null);
        return user != null ? user.getId() : null;
    }

    private Long resolvePatientId(Long userId) {
        if (userId == null) {
            return null;
        }
        return patientRepository.findByUserId(userId).map(p -> p.getId()).orElse(null);
    }

    private boolean isDoctorAssignedToPatient(Long patientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        // Allow admin
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        // Find doctor for current user
        User user = userRepository.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return false;
        }
        var docOpt = doctorRepository.findByUserId(user.getId());
        if (docOpt.isEmpty()) {
            return false;
        }
        var doc = docOpt.get();
        return assignmentRepository.findByDoctorIdAndPatientId(doc.getId(), patientId).isPresent();
    }

    @GetMapping("/weekly")
    public String myWeeklyReports(@RequestParam(required = false) Long userId,
            Model model,
            RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isDoctor = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isDoctorOrAdmin = isDoctor || isAdmin;

        if (!isDoctorOrAdmin) {
            // Bệnh nhân: luôn force về userId của chính mình
            userId = getCurrentUserId();
        } else if (userId != null && isDoctor) {
            // Bác sĩ truy cập userId khác: phải được phân công
            Long patientId = resolvePatientId(userId);
            if (patientId != null && !isDoctorAssignedToPatient(patientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem hồ sơ này.");
                return "redirect:/health-logs/kidney-risk/doctor/dashboard";
            }
        }

        Long patientId = resolvePatientId(userId);
        // isDoctorView = true khi bác sĩ/admin xem userId khác với chính mình
        boolean isDoctorView = isDoctorOrAdmin
                && userId != null
                && !userId.equals(getCurrentUserId());

        if (patientId == null) {
            model.addAttribute("reports", List.of());
            model.addAttribute("isDoctorView", isDoctorView);
            model.addAttribute("userId", userId);
            model.addAttribute("patientId", null);
            return "healthlog/weekly-kidney-risk";
        }

        List<WeeklyHealthReport> reports
                = weeklyHealthReportRepository.findByPatientIdOrderByWeekStartDesc(patientId);
        model.addAttribute("reports", reports);
        model.addAttribute("isDoctorView", isDoctorView);
        model.addAttribute("userId", userId);
        model.addAttribute("patientId", patientId);
        if (!reports.isEmpty()) {
            model.addAttribute("latestAssessment", reports.get(0));
        }
        return "healthlog/weekly-kidney-risk";
    }

    @GetMapping("/weekly/{patientId}")
    public String doctorViewWeekly(@PathVariable Long patientId, Model model, RedirectAttributes redirectAttributes) {
        if (!isDoctorAssignedToPatient(patientId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem hồ sơ này.");
            return "redirect:/health-logs/kidney-risk/doctor/dashboard";
        }
        List<WeeklyHealthReport> reports = weeklyHealthReportRepository.findByPatientIdOrderByWeekStartDesc(patientId);
        model.addAttribute("reports", reports);
        model.addAttribute("isDoctorView", true);
        Patient p = patientRepository.findById(patientId).orElse(null);
        model.addAttribute("patientName", p != null ? p.getFullName() : "Bệnh nhân");

        Long resolvedUserId = (p != null && p.getUser() != null) ? p.getUser().getId() : null;
        model.addAttribute("userId", resolvedUserId);
        model.addAttribute("patientId", patientId);

        if (!reports.isEmpty()) {
            model.addAttribute("latestAssessment", reports.get(0));
        }
        return "healthlog/weekly-kidney-risk";
    }

    @GetMapping("/doctor/dashboard")
    public String doctorDashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = auth != null ? userRepository.findByEmail(auth.getName()).orElse(null) : null;
        var doctor = user != null ? doctorRepository.findByUserId(user.getId()).orElse(null) : null;
        List<Patient> patients = List.of();
        if (doctor != null) {
            patients = assignmentRepository.findByDoctorIdAndStatus(doctor.getId(), "active")
                    .stream()
                    .map(a -> a.getPatient())
                    .filter(p -> p != null)
                    .toList();
        }
        model.addAttribute("patients", patients);
        return "doctor/kidney-risk-dashboard";
    }
}
