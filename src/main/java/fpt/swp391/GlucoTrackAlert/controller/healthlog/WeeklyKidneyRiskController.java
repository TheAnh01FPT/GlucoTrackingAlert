package fpt.swp391.GlucoTrackAlert.controller.healthlog;

import fpt.swp391.GlucoTrackAlert.dto.CustomRangeResult;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.model.risk.WeeklyHealthReport;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.risk.WeeklyHealthReportRepository;
import fpt.swp391.GlucoTrackAlert.service.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/health-logs/kidney-risk")
@RequiredArgsConstructor
public class WeeklyKidneyRiskController {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final WeeklyHealthReportRepository weeklyHealthReportRepository;
    private final WeeklyReportService weeklyReportService;
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

    // XÓA: Không cần kiểm tra doctor/admin - chỉ PATIENT xem

    @GetMapping("/weekly")
    public String myWeeklyReports(@RequestParam(required = false) Long userId,
            Model model,
            RedirectAttributes redirectAttributes) {
        // Chỉ PATIENT xem được - force userId về chính người dùng hiện tại
        userId = getCurrentUserId();

        Long patientId = resolvePatientId(userId);
        Patient patient = patientRepository.findByUserId(userId).orElse(null);
        // Không còn isDoctorView
        boolean isDoctorView = false;

        model.addAttribute("patient", patient);
        model.addAttribute("patientName", patient != null ? patient.getFullName() : null);

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
            WeeklyHealthReport previous = reports.size() > 1 ? reports.get(1) : null;
            model.addAttribute("previousAssessment", previous);
        }
        return "healthlog/weekly-kidney-risk";
    }

    @GetMapping("/custom-range")
    public String customRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long userId,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            redirectAttributes.addFlashAttribute("customRangeError", "Khoảng ngày không hợp lệ.");
            return "redirect:/health-logs/kidney-risk/weekly";
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) > 60) {
            redirectAttributes.addFlashAttribute("customRangeError", "Khoảng ngày tối đa 60 ngày.");
            return "redirect:/health-logs/kidney-risk/weekly";
        }

        // Chỉ PATIENT xem được - force userId về chính người dùng hiện tại
        userId = getCurrentUserId();

        Long patientId = resolvePatientId(userId);
        Patient patient = patientRepository.findByUserId(userId).orElse(null);
        if (patientId == null) {
            redirectAttributes.addFlashAttribute("customRangeError", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/health-logs/kidney-risk/weekly";
        }

        CustomRangeResult result = weeklyReportService.computeCustomRange(patientId, fromDate, toDate);

        List<WeeklyHealthReport> reports =
                weeklyHealthReportRepository.findByPatientIdOrderByWeekStartDesc(patientId);

        // Không còn isDoctorView
        boolean isDoctorView = false;

        model.addAttribute("patient", patient);
        model.addAttribute("patientName", patient != null ? patient.getFullName() : null);
        model.addAttribute("customRangeResult", result);
        model.addAttribute("reports", reports);
        model.addAttribute("isDoctorView", isDoctorView);
        model.addAttribute("userId", userId);
        model.addAttribute("patientId", patientId);
        if (!reports.isEmpty()) {
            model.addAttribute("latestAssessment", reports.get(0));
            WeeklyHealthReport previous = reports.size() > 1 ? reports.get(1) : null;
            model.addAttribute("previousAssessment", previous);
        }
        return "healthlog/weekly-kidney-risk";
    }
}
