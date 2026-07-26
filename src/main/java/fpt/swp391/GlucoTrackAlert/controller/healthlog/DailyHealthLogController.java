package fpt.swp391.GlucoTrackAlert.controller.healthlog;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogRequest;
import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.model.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.model.doctor.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.repository.doctor.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.doctor.DoctorPatientAssignmentRepository;
import java.util.Collections;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import fpt.swp391.GlucoTrackAlert.service.healthlog.DailyHealthLogService;
import fpt.swp391.GlucoTrackAlert.repository.healthlog.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.model.healthlog.DailyHealthLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.HashMap;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import fpt.swp391.GlucoTrackAlert.service.export.ExportService;
import fpt.swp391.GlucoTrackAlert.repository.risk.WeeklyHealthReportRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskAssessmentRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.RiskWarningRepository;
import fpt.swp391.GlucoTrackAlert.model.risk.WeeklyHealthReport;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskAssessment;
import fpt.swp391.GlucoTrackAlert.model.risk.RiskWarning;
import fpt.swp391.GlucoTrackAlert.service.strokeai.WeeklyStrokeAiService;

@Controller
@RequestMapping("/health-logs")
@RequiredArgsConstructor
@Slf4j
public class DailyHealthLogController {

    private final DailyHealthLogService dailyHealthLogService;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorPatientAssignmentRepository assignmentRepository;
    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ExportService exportService;
    private final WeeklyHealthReportRepository weeklyHealthReportRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskWarningRepository riskWarningRepository;
    private final WeeklyStrokeAiService weeklyStrokeAiService;

    private Long resolvePatientId(Long userId) {
        if (userId == null) {
            return null;
        }
        // Resolve by treating the parameter as a user ID and finding the linked patient.
        // Do NOT treat the parameter as a patient ID to avoid ambiguous collisions between
        // user IDs and patient IDs (was causing redirects to the wrong patient).
        Optional<Patient> patientOpt = patientRepository.findByUserId(userId);
        return patientOpt.map(Patient::getId).orElse(null);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        return user != null ? user.getId() : null;
    }

    private Long getCurrentPatientId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        Optional<Patient> patientOpt = patientRepository.findByUserId(userId);
        return patientOpt.map(Patient::getId).orElse(null);
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    private boolean checkViewOwnership(Long logPatientId) {
        Long currentPatientId = getCurrentPatientId();
        if (currentPatientId != null && currentPatientId.equals(logPatientId)) {
            return true;
        }
        if (hasRole("ROLE_ADMIN")) {
            return true;
        }
        if (hasRole("ROLE_DOCTOR")) {
            return isDoctorAssignedToPatient(logPatientId);
        }
        return false;
    }

    private boolean checkWriteOwnership(Long logPatientId) {
        Long currentPatientId = getCurrentPatientId();
        if (currentPatientId != null && currentPatientId.equals(logPatientId)) {
            return true;
        }
        if (hasRole("ROLE_ADMIN")) {
            return true;
        }
        return false;
    }

    @GetMapping
    public String getLogs(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) String patientType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        // Redirect về đúng trang theo role, tránh để /health-logs là trang chung
        if (hasRole("ROLE_DOCTOR") || hasRole("ROLE_ADMIN")) {
            return "redirect:/health-logs/doctor-view" + (userId != null ? "?userId=" + userId : "");
        }
        if (hasRole("ROLE_PATIENT")) {
            Long curUserId = getCurrentUserId();
            return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : "");
        }
        // Prevent non-admin/doctor users from viewing other patients by forcing
        // `userId` to the current logged-in user when the caller is not admin/doctor.
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            Long cur = getCurrentUserId();
            if (cur != null) {
                userId = cur;
            }
        }
        List<Patient> patients;
        if (patientType != null && !patientType.isEmpty()) {
            patients = patientRepository.findAllByStatusAndPatientType("active", patientType);
            if (patients.isEmpty()) {
                patients = patientRepository.findAllByStatus("active");
            }
        } else {
            patients = patientRepository.findAllByStatus("active");
            if (patients.isEmpty()) {
                patients = patientRepository.findAll();
            }
        }
        model.addAttribute("patients", patients);
        model.addAttribute("patientType", patientType);

        Long selectedPatientId = userId; // userId từ dropdown đã là patient.id rồi
        if (selectedPatientId == null && !patients.isEmpty()) {
            selectedPatientId = patients.get(0).getId();
        }

        if (selectedPatientId != null) {
            Pageable pageable = PageRequest.of(page, size);
            Page<DailyHealthLogResponse> logsPage = dailyHealthLogService.getLogs(selectedPatientId, pageable);
            model.addAttribute("logs", logsPage.getContent());
            model.addAttribute("latestLog", dailyHealthLogRepository.findFirstByPatientIdOrderByLogDateDesc(selectedPatientId));
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", logsPage.getTotalPages());
            model.addAttribute("totalElements", logsPage.getTotalElements());
            model.addAttribute("pageSize", size);
            model.addAttribute("selectedUserId", selectedPatientId);
            model.addAttribute("selectedPatientId", selectedPatientId);
        } else {
            model.addAttribute("logs", Collections.emptyList());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalElements", 0L);
            model.addAttribute("pageSize", size);
            model.addAttribute("selectedUserId", null);
            model.addAttribute("selectedPatientId", null);
        }

        return "healthlog/list";
    }

    @GetMapping("/doctor-view")
    public String getDoctorView(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) String patientType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            RedirectAttributes redirectAttributes) {
        List<Patient> patients;

        if (hasRole("ROLE_ADMIN")) {
            // Admin xem tất cả
            if (patientType != null && !patientType.isEmpty()) {
                patients = patientRepository.findAllByStatusAndPatientType("active", patientType);
                if (patients.isEmpty()) {
                    patients = patientRepository.findAllByStatus("active");
                }
            } else {
                patients = patientRepository.findAllByStatus("active");
                if (patients.isEmpty()) {
                    patients = patientRepository.findAll();
                }
            }
        } else if (hasRole("ROLE_DOCTOR")) {
            Long currentUserId = getCurrentUserId();
            Doctor doctor = doctorRepository.findByUserId(currentUserId).orElse(null);
            if (doctor == null) {
                model.addAttribute("error", "Không tìm thấy hồ sơ bác sĩ. Vui lòng liên hệ admin.");
                model.addAttribute("patients", Collections.emptyList());
                model.addAttribute("patientType", patientType);
                model.addAttribute("selectedUserId", null);
                model.addAttribute("logs", Collections.emptyList());
                model.addAttribute("currentPage", 0);
                model.addAttribute("totalPages", 0);
                model.addAttribute("totalElements", 0L);
                model.addAttribute("pageSize", size);
                return "healthlog/doctor-view";
            }
            List<DoctorPatientAssignment> assignments
                    = assignmentRepository.findByDoctorIdAndStatus(doctor.getId(), "active");
            patients = assignments.stream()
                    .map(DoctorPatientAssignment::getPatient)
                    .filter(p -> "active".equals(p.getStatus()))
                    .collect(Collectors.toList());

            // Lọc thêm theo patientType nếu có
            if (patientType != null && !patientType.isEmpty()) {
                String finalPatientType = patientType;
                patients = patients.stream()
                        .filter(p -> finalPatientType.equals(p.getPatientType()))
                        .collect(Collectors.toList());
            }
        } else {
            return "redirect:/login";
        }
        model.addAttribute("patients", patients);
        model.addAttribute("patientType", patientType);

        // Only allow passing arbitrary userId when caller is admin/doctor.
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            Long cur = getCurrentUserId();
            if (cur != null) {
                userId = cur;
            }
        }

        Long selectedPatientId = userId; // userId từ dropdown đã là patient.id rồi
        if (selectedPatientId == null && !patients.isEmpty()) {
            selectedPatientId = patients.get(0).getId();
        }
        // If caller is a doctor, ensure they are assigned to the selected patient
        if (hasRole("ROLE_DOCTOR") && selectedPatientId != null) {
            if (!isDoctorAssignedToPatient(selectedPatientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không được phân công cho bệnh nhân này");
                return "redirect:/health-logs/doctor-view";
            }
        }
        Patient selectedPatient = null;
        if (selectedPatientId != null) {
            Optional<Patient> patientOpt = patientRepository.findById(selectedPatientId);
            if (patientOpt.isPresent()) {
                selectedPatient = patientOpt.get();
            }

            // Trigger weekly AI prediction calculation (On-Demand)
            try {
                dailyHealthLogService.assessWeeklyRisk(selectedPatientId);
            } catch (Exception e) {
                log.error("assessWeeklyRisk failed for patientId={}", selectedPatientId, e);
            }

            // Get the latest weekly AI prediction for this patient
            List<Map<String, Object>> riskList = jdbcTemplate.queryForList(
                    "SELECT ra.id, ra.risk_percentage, ra.risk_level, ra.ai_summary, ra.recommendation, ra.assessed_at "
                    + "FROM risk_assessments ra "
                    + "WHERE ra.patient_id = ? AND ra.assessment_type = 'WEEKLY_AI_PREDICTION' "
                    + "ORDER BY ra.assessed_at DESC LIMIT 1",
                    selectedPatientId
            );

            Map<String, Object> latestRisk = null;
            if (!riskList.isEmpty()) {
                Map<String, Object> raw = riskList.get(0);
                latestRisk = new HashMap<>();
                Object pct = raw.get("risk_percentage");
                if (pct instanceof java.math.BigDecimal) {
                    latestRisk.put("riskPercentage", ((java.math.BigDecimal) pct).setScale(2, java.math.RoundingMode.HALF_UP));
                } else if (pct instanceof Number) {
                    latestRisk.put("riskPercentage", java.math.BigDecimal.valueOf(((Number) pct).doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP));
                } else {
                    latestRisk.put("riskPercentage", java.math.BigDecimal.ZERO);
                }

                latestRisk.put("riskLevel", raw.get("risk_level"));
                latestRisk.put("aiSummary", raw.get("ai_summary"));
                latestRisk.put("recommendation", raw.get("recommendation"));

                Object assessedAtObj = raw.get("assessed_at");
                if (assessedAtObj != null) {
                    if (assessedAtObj instanceof java.time.LocalDateTime) {
                        java.time.LocalDateTime ldt = (java.time.LocalDateTime) assessedAtObj;
                        latestRisk.put("assessedAtStr", ldt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                    } else if (assessedAtObj instanceof java.util.Date) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
                        latestRisk.put("assessedAtStr", sdf.format((java.util.Date) assessedAtObj));
                    } else {
                        latestRisk.put("assessedAtStr", assessedAtObj.toString());
                    }
                }
            }
            model.addAttribute("latestRisk", latestRisk);
        model.addAttribute("latestAssessment", latestRisk);

            Pageable pageable = PageRequest.of(page, size);
            Page<DailyHealthLogResponse> logsPage = dailyHealthLogService.getLogs(selectedPatientId, pageable);
            model.addAttribute("logs", logsPage.getContent());
            model.addAttribute("latestLog", dailyHealthLogRepository.findFirstByPatientIdOrderByLogDateDesc(selectedPatientId));
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", logsPage.getTotalPages());
            model.addAttribute("totalElements", logsPage.getTotalElements());
            model.addAttribute("pageSize", size);
            model.addAttribute("selectedUserId", selectedPatientId);
            model.addAttribute("selectedPatientId", selectedPatientId);
        } else {
            model.addAttribute("latestRisk", null);
            model.addAttribute("logs", Collections.emptyList());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalElements", 0L);
            model.addAttribute("pageSize", size);
            model.addAttribute("selectedUserId", null);
            model.addAttribute("selectedPatientId", null);
        }

        model.addAttribute("selectedPatient", selectedPatient);
        return "healthlog/doctor-view";
    }

    @GetMapping("/my-logs")
    public String getMyLogs(@RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            RedirectAttributes redirectAttributes,
            Model model) {
        // If caller is not admin/doctor and the requested userId is not their own,
        // redirect to login to prevent tampering with the `userId` query parameter.
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            Long curUserId = getCurrentUserId();
            if (curUserId == null || !curUserId.equals(userId)) {
                return "redirect:/login";
            }
        }

        Long patientId = resolvePatientId(userId);
        if (patientId == null) {
            return "redirect:/login"; // hoặc trang lỗi
        }

        // Doctor không được đi vòng qua màn "của bệnh nhân" để xem log của
        // bệnh nhân không do mình phụ trách. Chặn cứng, hướng họ về doctor-view
        // (nơi đã có check assignment đúng).
        if (hasRole("ROLE_DOCTOR") && !isDoctorAssignedToPatient(patientId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không được phân công cho bệnh nhân này.");
            return "redirect:/health-logs/doctor-view";
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<DailyHealthLogResponse> logsPage = dailyHealthLogService.getLogs(patientId, pageable);
        model.addAttribute("logs", logsPage.getContent());
        model.addAttribute("latestLog", dailyHealthLogRepository.findFirstByPatientIdOrderByLogDateDesc(patientId));
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logsPage.getTotalPages());
        model.addAttribute("totalElements", logsPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("userId", userId);
        model.addAttribute("patientId", patientId);
        return "healthlog/patient-logs";
    }

    @GetMapping("/{id}")
    public String getLogById(@PathVariable Long id,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String source,
            Model model,
            RedirectAttributes redirectAttributes) {
        DailyHealthLogResponse log = dailyHealthLogService.getLogById(id);
        if (log == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy nhật ký");
            return "redirect:/health-logs";
        }
        if (!checkViewOwnership(log.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền truy cập nhật ký này");
            Long curUserId = getCurrentUserId();
            if (curUserId == null) {
                curUserId = userId;
            }
            return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : "");
        }

        // checkViewOwnership() đã xử lý cả ADMIN và DOCTOR được phân công
        // không cần check lại lần 2 ở đây (trước đây check lại gây Admin bị redirect nhầm)
        model.addAttribute("log", log);
        model.addAttribute("source", source);
        return "healthlog/detail";
    }

    @GetMapping("/detail")
    public String redirectOldDetail(@RequestParam Long logId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String source) {
        String redirectUrl = "/health-logs/" + logId;
        if (userId != null) {
            redirectUrl += "?userId=" + userId;
        }
        if (source != null) {
            redirectUrl += (userId != null ? "&" : "?") + "source=" + source;
        }
        return "redirect:" + redirectUrl;
    }

    /**
     * Kiểm tra doctor hiện tại có được phân công bệnh nhân này không. Admin
     * luôn trả về true.
     */
    private boolean isDoctorAssignedToPatient(Long patientId) {
        if (hasRole("ROLE_ADMIN")) {
            return true;
        }
        if (!hasRole("ROLE_DOCTOR")) {
            return false;
        }
        Long currentUserId = getCurrentUserId();
        Doctor doctor = doctorRepository.findByUserId(currentUserId).orElse(null);
        if (doctor == null) {
            return false;
        }

        Optional<DoctorPatientAssignment> assignment = assignmentRepository.findByDoctorIdAndPatientId(doctor.getId(), patientId);
        return assignment.isPresent() && "active".equalsIgnoreCase(assignment.get().getStatus());
    }

    private String friendlyErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
            return "Không thể thực hiện thao tác này do dữ liệu đang được tham chiếu ở nơi khác. Vui lòng liên hệ quản trị viên.";
        }
        if (msg != null && (msg.contains("Không tìm thấy")
                || msg.contains("đã nhập nhật ký")
                || msg.contains("không có quyền")
                || msg.contains("quá 3 ngày"))) {
            return msg;
        }
        if (msg != null && (msg.contains("Không tìm thấy")
                || msg.contains("đã nhập nhật ký")
                || msg.contains("không có quyền"))) {
            return msg;
        }
        return "Có lỗi xảy ra, vui lòng thử lại hoặc liên hệ quản trị viên.";

    }

    @GetMapping("/create")
    public String createLogForm(@RequestParam Long userId,
            @RequestParam(required = false) String source,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!hasRole("ROLE_ADMIN")) {
            Long curUserId = getCurrentUserId();
            if (curUserId == null || !curUserId.equals(userId)) {
                if (hasRole("ROLE_DOCTOR")) {
                    redirectAttributes.addFlashAttribute("error", "Bác sĩ không có quyền tạo nhật ký hộ bệnh nhân.");
                    return "redirect:/health-logs/doctor-view";
                }
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền tạo nhật ký cho người dùng khác.");
                return "redirect:/login";
            }
        }

        if (!model.containsAttribute("log")) {
            DailyHealthLogRequest request = new DailyHealthLogRequest();
            request.setLogDate(LocalDate.now());
            model.addAttribute("log", request);
        }
        model.addAttribute("userId", userId);
        model.addAttribute("source", source);
        model.addAttribute("action", "/health-logs/create?userId=" + userId + "&source=" + (source != null ? source : "my-logs"));
        return "healthlog/form";
    }

    @PostMapping("/create")
    public String createLog(@RequestParam Long userId,
            @RequestParam(required = false) String source,
            @Valid @ModelAttribute("log") DailyHealthLogRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (!hasRole("ROLE_ADMIN")) {
            Long curUserId = getCurrentUserId();
            if (curUserId == null || !curUserId.equals(userId)) {
                if (hasRole("ROLE_DOCTOR")) {
                    redirectAttributes.addFlashAttribute("error", "Bác sĩ không có quyền tạo nhật ký hộ bệnh nhân.");
                    return "redirect:/health-logs/doctor-view";
                }
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền tạo nhật ký cho người dùng khác.");
                return "redirect:/login";
            }
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.log", bindingResult);
            redirectAttributes.addFlashAttribute("log", request);
            String redirectUrl = "redirect:/health-logs/create?userId=" + userId;
            if ("my-logs".equals(source)) {
                redirectUrl += "&source=my-logs";
            } else if ("doctor-view".equals(source)) {
                redirectUrl += "&source=doctor-view";
            }
            return redirectUrl;
        }

        Long patientId = resolvePatientId(userId);
        if (patientId == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Không tìm thấy thông tin bệnh nhân tương ứng với ID: " + userId);
            if ("my-logs".equals(source)) {
                return "redirect:/health-logs/my-logs?userId=" + userId;
            }
            return "redirect:/health-logs?userId=" + userId;
        }

        try {
            dailyHealthLogService.createLog(patientId, request);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", friendlyErrorMessage(e));
            redirectAttributes.addFlashAttribute("log", request);
            String redirectUrl = "redirect:/health-logs/create?userId=" + userId;
            if ("my-logs".equals(source)) {
                redirectUrl += "&source=my-logs";
            } else if ("doctor-view".equals(source)) {
                redirectUrl += "&source=doctor-view";
            }
            return redirectUrl;
        }

        if ("my-logs".equals(source)) {
            return "redirect:/health-logs/my-logs?userId=" + userId;
        } else if ("doctor-view".equals(source)) {
            return "redirect:/health-logs/doctor-view?userId=" + userId;
        }
        return "redirect:/health-logs?userId=" + userId;
    }

    @GetMapping("/{id}/edit")
    public String editLogForm(@PathVariable Long id,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String source,
            Model model,
            RedirectAttributes redirectAttributes) {
        DailyHealthLogResponse response = dailyHealthLogService.getLogById(id);

        if (response == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy nhật ký");
            return "redirect:/health-logs";
        }
        if (!checkWriteOwnership(response.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Nhật ký sức khỏe chỉ do bệnh nhân tự nhập, bác sĩ không có quyền chỉnh sửa.");
            Long curUserId = getCurrentUserId();
            return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : "");
        }

        if (!model.containsAttribute("log")) {
            DailyHealthLogRequest request = new DailyHealthLogRequest();
            request.setLogDate(response.getLogDate());
            request.setBloodSugar(response.getBloodSugar());
            request.setSystolic(response.getSystolic());
            request.setDiastolic(response.getDiastolic());
            request.setSleepHours(response.getSleepHours());
            request.setWaterMl(response.getWaterMl());
            request.setSugarConsumptionLevel(response.getSugarConsumptionLevel());
            request.setSymptoms(response.getSymptoms());
            request.setNote(response.getNote());
            request.setPhysicalActivity(response.getPhysicalActivity());
            model.addAttribute("log", request);
        }
        model.addAttribute("userId", userId != null ? userId : response.getUserId());
        model.addAttribute("source", source); // ← THÊM DÒNG NÀY

        String actionUrl = "/health-logs/" + id + "/edit?userId=" + (userId != null ? userId : response.getUserId());
        if ("my-logs".equals(source)) {
            actionUrl += "&source=my-logs";
        } else if ("doctor-view".equals(source)) {
            actionUrl += "&source=doctor-view"; // ← THÊM DÒNG NÀY
        }
        model.addAttribute("action", actionUrl);
        return "healthlog/form";
    }

    @PostMapping("/{id}/edit")
    public String updateLog(@PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam(required = false) String source,
            @Valid @ModelAttribute("log") DailyHealthLogRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.log", bindingResult);
            redirectAttributes.addFlashAttribute("log", request);
            String redirectUrl = "redirect:/health-logs/" + id + "/edit?userId=" + userId;
            if ("my-logs".equals(source)) {
                redirectUrl += "&source=my-logs";
            } else if ("doctor-view".equals(source)) {
                redirectUrl += "&source=doctor-view";
            }
            return redirectUrl;
        }

        DailyHealthLogResponse response = dailyHealthLogService.getLogById(id);
        if (response == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy nhật ký");
            return "redirect:/health-logs";
        }
        if (!checkWriteOwnership(response.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Nhật ký sức khỏe chỉ do bệnh nhân tự nhập, bác sĩ không có quyền chỉnh sửa.");
            Long curUserId = getCurrentUserId();
            if ("my-logs".equals(source)) {
                return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : userId);
            }
            return "redirect:/health-logs?userId=" + (curUserId != null ? curUserId : userId);
        }

        try {
            dailyHealthLogService.updateLog(id, request);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", friendlyErrorMessage(e));
            redirectAttributes.addFlashAttribute("log", request);
            String redirectUrl = "redirect:/health-logs/" + id + "/edit?userId=" + userId;
            if ("my-logs".equals(source)) {
                redirectUrl += "&source=my-logs";
            } else if ("doctor-view".equals(source)) {
                redirectUrl += "&source=doctor-view";
            }
            return redirectUrl;
        }
        if ("my-logs".equals(source)) {
            return "redirect:/health-logs/my-logs?userId=" + userId;
        } else if ("doctor-view".equals(source)) {
            return "redirect:/health-logs/doctor-view?userId=" + userId;
        }
        return "redirect:/health-logs?userId=" + userId;
    }

    @PostMapping("/{id}/delete")
    public String deleteLog(@PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam(required = false) String source,
            RedirectAttributes redirectAttributes) {
        DailyHealthLogResponse response = dailyHealthLogService.getLogById(id);
        if (response == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy nhật ký");
            return "redirect:/health-logs";
        }
        if (!checkWriteOwnership(response.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Nhật ký sức khỏe chỉ do bệnh nhân tự nhập, bác sĩ không có quyền chỉnh sửa.");
            Long curUserId = getCurrentUserId();
            return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : userId);
        }

        try {
            dailyHealthLogService.deleteLog(id);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", friendlyErrorMessage(ex));
            if ("my-logs".equals(source)) {
                return "redirect:/health-logs/my-logs?userId=" + userId;
            } else if ("doctor-view".equals(source)) {
                return "redirect:/health-logs/doctor-view?userId=" + userId;
            }
            return "redirect:/health-logs?userId=" + userId;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", friendlyErrorMessage(ex));
            if ("my-logs".equals(source)) {
                return "redirect:/health-logs/my-logs?userId=" + userId;
            } else if ("doctor-view".equals(source)) {
                return "redirect:/health-logs/doctor-view?userId=" + userId;
            }
            return "redirect:/health-logs?userId=" + userId;
        }

        if ("my-logs".equals(source)) {
            return "redirect:/health-logs/my-logs?userId=" + userId;
        } else if ("doctor-view".equals(source)) {
            return "redirect:/health-logs/doctor-view?userId=" + userId;
        }
        return "redirect:/health-logs?userId=" + userId;
    }

    @GetMapping("/chart")
    public String getChart(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            RedirectAttributes redirectAttributes,
            Model model) {
        List<Patient> patients;
        if (hasRole("ROLE_DOCTOR") && !hasRole("ROLE_ADMIN")) {
            // Bác sĩ chỉ được thấy/xem bệnh nhân do mình phụ trách, không phải toàn bộ hệ thống.
            Long currentUserId = getCurrentUserId();
            Doctor doctor = doctorRepository.findByUserId(currentUserId).orElse(null);
            patients = doctor == null ? Collections.emptyList()
                    : assignmentRepository.findByDoctorIdAndStatus(doctor.getId(), "active").stream()
                            .map(fpt.swp391.GlucoTrackAlert.model.doctor.DoctorPatientAssignment::getPatient)
                            .filter(java.util.Objects::nonNull)
                            .toList();
        } else {
            patients = patientRepository.findAllByStatus("active");
            if (patients.isEmpty()) {
                patients = patientRepository.findAll();
            }
        }
        model.addAttribute("patients", patients);

        // Non-admin/doctor cannot change which patient's chart they view.
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            Long cur = getCurrentUserId();
            if (cur != null) {
                userId = cur;
            }
        }

        Long selectedPatientId = resolvePatientId(userId);
        if (selectedPatientId == null && !patients.isEmpty()) {
            selectedPatientId = patients.get(0).getId();
        }

        // Bác sĩ không được xem chart của bệnh nhân không do mình phụ trách,
        // kể cả nếu họ tự sửa tham số userId trên URL.
        if (hasRole("ROLE_DOCTOR") && selectedPatientId != null && !isDoctorAssignedToPatient(selectedPatientId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không được phân công cho bệnh nhân này.");
            return "redirect:/health-logs/doctor-view";
        }

        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(30);

        if (selectedPatientId != null) {
            List<DailyHealthLogResponse> chartData = dailyHealthLogService.getChartData(selectedPatientId, startDate,
                    endDate);
            model.addAttribute("chartData", chartData);
            model.addAttribute("selectedUserId", selectedPatientId);
            model.addAttribute("selectedPatientId", selectedPatientId);
        } else {
            model.addAttribute("chartData", Collections.emptyList());
            model.addAttribute("selectedUserId", null);
            model.addAttribute("selectedPatientId", null);
        }

        model.addAttribute("from", startDate);
        model.addAttribute("to", endDate);
        return "healthlog/chart";
    }

    @GetMapping("/my-chart")
    public String getMyChart(@RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            RedirectAttributes redirectAttributes,
            Model model) {
        // Prevent IDOR: non-admin/doctor users may only view their own chart,
        // regardless of what userId is passed in the request.
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            Long cur = getCurrentUserId();
            if (cur != null) {
                userId = cur;
            }
        }

        Long patientId = resolvePatientId(userId);
        if (patientId == null) {
            patientId = userId;
        }

        // Doctor không được xem chart của bệnh nhân không do mình phụ trách.
        if (hasRole("ROLE_DOCTOR") && !isDoctorAssignedToPatient(patientId)) {
            redirectAttributes.addFlashAttribute("error", "Bạn không được phân công cho bệnh nhân này.");
            return "redirect:/health-logs/doctor-view";
        }

        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(30);

        List<DailyHealthLogResponse> chartData = dailyHealthLogService.getChartData(patientId, startDate, endDate);
        model.addAttribute("chartData", chartData);
        model.addAttribute("userId", userId);
        model.addAttribute("from", startDate);
        model.addAttribute("to", endDate);
        return "healthlog/my-chart";
    }

    @GetMapping("/doctor-chart")
    public String getDoctorChart(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model,
            RedirectAttributes redirectAttributes) {
        // Chỉ DOCTOR/ADMIN mới vào được
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            return "redirect:/login";
        }

        // Lọc danh sách bệnh nhân theo role -- giống getDoctorView()
        // Trước đây lấy tất cả active patients -> bác sĩ A xem được chart bệnh nhân của bác sĩ B
        List<Patient> patients;
        if (hasRole("ROLE_ADMIN")) {
            patients = patientRepository.findAllByStatus("active");
            if (patients.isEmpty()) {
                patients = patientRepository.findAll();
            }
        } else {
            Long currentUserId = getCurrentUserId();
            Doctor doctor = doctorRepository.findByUserId(currentUserId).orElse(null);
            if (doctor == null) {
                model.addAttribute("patients", Collections.emptyList());
                model.addAttribute("chartData", Collections.emptyList());
                model.addAttribute("selectedUserId", null);
                model.addAttribute("from", LocalDate.now().minusDays(30));
                model.addAttribute("to", LocalDate.now());
                return "healthlog/doctor-chart";
            }
            List<DoctorPatientAssignment> assignments = assignmentRepository.findByDoctorIdAndStatus(doctor.getId(), "active");
            patients = assignments.stream().map(DoctorPatientAssignment::getPatient).collect(Collectors.toList());
        }
        model.addAttribute("patients", patients);

        Long selectedPatientId = userId;
        if (selectedPatientId == null && !patients.isEmpty()) {
            selectedPatientId = patients.get(0).getId();
        }

        if (hasRole("ROLE_DOCTOR") && selectedPatientId != null) {
            if (!isDoctorAssignedToPatient(selectedPatientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không được phân công cho bệnh nhân này");
                return "redirect:/health-logs/doctor-view";
            }
        }

        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(30);

        if (selectedPatientId != null) {
            List<DailyHealthLogResponse> chartData = dailyHealthLogService.getChartData(selectedPatientId, startDate, endDate);
            model.addAttribute("chartData", chartData);
            model.addAttribute("selectedUserId", selectedPatientId);
        } else {
            model.addAttribute("chartData", Collections.emptyList());
            model.addAttribute("selectedUserId", null);
        }

        model.addAttribute("from", startDate);
        model.addAttribute("to", endDate);
        return "healthlog/doctor-chart";
    }

    @GetMapping("/ai-report")
    public String getAiReport(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String chartMonth,
            Model model, RedirectAttributes redirectAttributes) {
        Long curUserId = getCurrentUserId();
        if (curUserId == null) {
            return "redirect:/login";
        }

        boolean isDoctorOrAdminCaller = hasRole("ROLE_ADMIN") || hasRole("ROLE_DOCTOR");

        if (patientId == null) {
            if (userId != null) {
                Optional<Patient> pOpt = patientRepository.findByUserId(userId);
                if (pOpt.isPresent()) {
                    patientId = pOpt.get().getId();
                } else if (patientRepository.existsById(userId)) {
                    patientId = userId;
                }
            }
        }

        if (patientId == null) {
            if (isDoctorOrAdminCaller) {
                redirectAttributes.addFlashAttribute("error", "Thiếu thông tin bệnh nhân cần xem.");
                return "redirect:/health-logs/doctor-view";
            } else {
                // patient caller: resolve their own patient id
                patientId = resolvePatientId(curUserId);
            }
        }

        if (patientId == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/";
        }

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/";
        }

        // Access check: patient callers can only view their own record
        if (!isDoctorOrAdminCaller) {
            Long ownPatientId = resolvePatientId(curUserId);
            if (ownPatientId == null || !ownPatientId.equals(patientId)) {
                return "redirect:/login";
            }
        } else {
            // doctor/admin must be assigned to patient
            if (hasRole("ROLE_DOCTOR") && !isDoctorAssignedToPatient(patientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem báo cáo của bệnh nhân này.");
                return "redirect:/health-logs/doctor-view";
            }
        }

        // 1. Monthly health logs stats & chart data
        YearMonth selectedMonth = YearMonth.now();
        if (chartMonth != null && !chartMonth.isBlank()) {
            try {
                selectedMonth = YearMonth.parse(chartMonth);
            } catch (Exception ignored) {
                selectedMonth = YearMonth.now();
            }
        }

        LocalDate monthStart = selectedMonth.atDay(1);
        LocalDate monthEnd = selectedMonth.atEndOfMonth();
        List<DailyHealthLog> monthLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetween(patientId, monthStart, monthEnd);
        monthLogs.sort(java.util.Comparator.comparing(DailyHealthLog::getLogDate));

        List<String> chartDays = new java.util.ArrayList<>();
        List<Object> chartSugar = new java.util.ArrayList<>();
        List<Object> chartSystolic = new java.util.ArrayList<>();
        List<Object> chartDiastolic = new java.util.ArrayList<>();
        List<Object> chartSleep = new java.util.ArrayList<>();
        List<Object> chartWater = new java.util.ArrayList<>();

        for (int day = 1; day <= selectedMonth.lengthOfMonth(); day++) {
            LocalDate currentDate = selectedMonth.atDay(day);
            chartDays.add(String.valueOf(day));
            DailyHealthLog dayLog = monthLogs.stream()
                    .filter(log -> currentDate.equals(log.getLogDate()))
                    .findFirst()
                    .orElse(null);
            if (dayLog == null) {
                chartSugar.add(null);
                chartSystolic.add(null);
                chartDiastolic.add(null);
                chartSleep.add(null);
                chartWater.add(null);
                continue;
            }
            chartSugar.add(dayLog.getBloodSugar() != null ? dayLog.getBloodSugar().doubleValue() : null);
            chartSystolic.add(dayLog.getSystolic() != null ? dayLog.getSystolic() : null);
            chartDiastolic.add(dayLog.getDiastolic() != null ? dayLog.getDiastolic() : null);
            chartSleep.add(dayLog.getSleepHours() != null ? dayLog.getSleepHours().doubleValue() : null);
            chartWater.add(dayLog.getWaterMl() != null ? dayLog.getWaterMl() : null);
        }

        BigDecimal monthlyAvgSugar = calculateAverage(monthLogs, log -> log.getBloodSugar());
        BigDecimal monthlyAvgSystolic = calculateAverage(monthLogs, log -> log.getSystolic() != null ? BigDecimal.valueOf(log.getSystolic()) : null);
        BigDecimal monthlyAvgDiastolic = calculateAverage(monthLogs, log -> log.getDiastolic() != null ? BigDecimal.valueOf(log.getDiastolic()) : null);
        BigDecimal monthlyAvgSleep = calculateAverage(monthLogs, log -> log.getSleepHours());
        BigDecimal monthlyAvgWater = calculateAverage(monthLogs, log -> log.getWaterMl() != null ? BigDecimal.valueOf(log.getWaterMl()) : null);

        List<Map<String, Object>> monthOptions = new java.util.ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth optionMonth = currentMonth.minusMonths(i);
            Map<String, Object> option = new HashMap<>();
            option.put("value", optionMonth.toString());
            option.put("label", String.format(Locale.US, "Tháng %d/%d", optionMonth.getMonthValue(), optionMonth.getYear()));
            option.put("selected", optionMonth.equals(selectedMonth));
            monthOptions.add(option);
        }

        boolean isDocOrAdmin = hasRole("ROLE_DOCTOR") || hasRole("ROLE_ADMIN");
        model.addAttribute("isDoctorOrAdmin", isDocOrAdmin);

        model.addAttribute("patient", patient);
        Long targetUserId = patient.getUser() != null ? patient.getUser().getId() : null;
        model.addAttribute("userId", targetUserId);
        model.addAttribute("patientId", patientId);

        // Put the monthly models
        model.addAttribute("chartMonth", selectedMonth.toString());
        model.addAttribute("chartMonthLabel", String.format(Locale.US, "Tháng %d/%d", selectedMonth.getMonthValue(), selectedMonth.getYear()));
        model.addAttribute("chartDays", chartDays);
        model.addAttribute("chartSugar", chartSugar);
        model.addAttribute("chartSystolic", chartSystolic);
        model.addAttribute("chartDiastolic", chartDiastolic);
        model.addAttribute("chartSleep", chartSleep);
        model.addAttribute("chartWater", chartWater);
        model.addAttribute("monthlyAvgSugar", formatMetricValue(monthlyAvgSugar));
        model.addAttribute("monthlyAvgSystolic", formatMetricValue(monthlyAvgSystolic));
        model.addAttribute("monthlyAvgDiastolic", formatMetricValue(monthlyAvgDiastolic));
        model.addAttribute("monthlyAvgSleep", formatMetricValue(monthlyAvgSleep));
        model.addAttribute("monthlyAvgWater", formatMetricValue(monthlyAvgWater));
        model.addAttribute("monthOptions", monthOptions);
        model.addAttribute("monthlyLogCount", monthLogs.size());

        return "healthlog/ai-report";
    }

    @GetMapping("/stroke-risk")
    public String getStrokeRisk(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long patientId,
            Model model, RedirectAttributes redirectAttributes) {
        Long curUserId = getCurrentUserId();
        if (curUserId == null) {
            return "redirect:/login";
        }

        boolean isDoctorOrAdminCaller = hasRole("ROLE_ADMIN") || hasRole("ROLE_DOCTOR");

        if (patientId == null) {
            if (userId != null) {
                Optional<Patient> pOpt = patientRepository.findByUserId(userId);
                if (pOpt.isPresent()) {
                    patientId = pOpt.get().getId();
                } else if (patientRepository.existsById(userId)) {
                    patientId = userId;
                }
            }
        }

        if (patientId == null) {
            if (isDoctorOrAdminCaller) {
                redirectAttributes.addFlashAttribute("error", "Thiếu thông tin bệnh nhân cần xem.");
                return "redirect:/health-logs/doctor-view";
            } else {
                patientId = resolvePatientId(curUserId);
            }
        }

        if (patientId == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/";
        }

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/";
        }

        if (!isDoctorOrAdminCaller) {
            Long ownPatientId = resolvePatientId(curUserId);
            if (ownPatientId == null || !ownPatientId.equals(patientId)) {
                return "redirect:/login";
            }
        } else {
            if (hasRole("ROLE_DOCTOR") && !isDoctorAssignedToPatient(patientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem báo cáo của bệnh nhân này.");
                return "redirect:/health-logs/doctor-view";
            }
        }

        boolean isDoctorView = isDoctorOrAdminCaller
                && userId != null
                && !userId.equals(curUserId);

        List<WeeklyHealthReport> dbReports = weeklyHealthReportRepository.findByPatientIdOrderByWeekStartDesc(patientId);
        List<Map<String, Object>> reports = dbReports.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("weekStart", r.getWeekStart());
            map.put("weekEnd", r.getWeekEnd());
            map.put("averageBloodSugar", r.getAverageBloodSugar());
            map.put("averageSystolic", r.getAverageSystolic());
            map.put("averageDiastolic", r.getAverageDiastolic());
            map.put("createdAt", r.getCreatedAt());

            Optional<RiskAssessment> strokeAss = riskAssessmentRepository.findByWeeklyReportIdAndAssessmentType(r.getId(), "WEEKLY_STROKE_RISK");
            if (strokeAss.isPresent()) {
                RiskAssessment ass = strokeAss.get();
                map.put("healthStatus", ass.getRiskLevel());
                map.put("riskPercentage", ass.getRiskPercentage());
                map.put("recommendation", ass.getRecommendation());
                map.put("lowConfidence", ass.getLowConfidence());
                map.put("aiSummary", ass.getAiSummary());
            } else {
                try {
                    Map<String, Object> aiStrokeResult = weeklyStrokeAiService.calculateWeeklyStrokeRisk(patient, r.getWeekStart(), r.getWeekEnd());
                    if (aiStrokeResult != null && !aiStrokeResult.isEmpty()) {
                        Double riskPercentage = null;
                        if (aiStrokeResult.get("risk_percentage") != null) {
                            riskPercentage = Double.parseDouble(aiStrokeResult.get("risk_percentage").toString());
                        }
                        String rawRiskLevel = (String) aiStrokeResult.get("risk_level");

                        String mappedLevel = "LOW";
                        String strokeAdvice = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ đột quỵ thấp.";

                        if (rawRiskLevel != null) {
                            String lowerLevel = rawRiskLevel.toLowerCase();
                            if (lowerLevel.contains("critical") || (riskPercentage != null && riskPercentage >= 75)) {
                                mappedLevel = "CRITICAL";
                                strokeAdvice = "🚨 Nguy cơ đột quỵ rất cao (Nguy hiểm)! Cần tham vấn bác sĩ ngay để kiểm soát huyết áp và các chỉ số sức khỏe.";
                            } else if (lowerLevel.contains("high") || (riskPercentage != null && riskPercentage >= 50)) {
                                mappedLevel = "HIGH";
                                strokeAdvice = "⚠️ Nguy cơ đột quỵ cao! Bạn nên điều chỉnh chế độ sinh hoạt, hạn chế các chất kích thích và theo dõi huyết áp thường xuyên.";
                            } else if (lowerLevel.contains("medium") || lowerLevel.contains("moderate") || (riskPercentage != null && riskPercentage >= 25)) {
                                mappedLevel = "MEDIUM";
                                strokeAdvice = "⚡ Nguy cơ đột quỵ ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                            }
                        }

                        BigDecimal finalPct = BigDecimal.valueOf(riskPercentage != null ? riskPercentage : 0.0).setScale(2, RoundingMode.HALF_UP);
                        
                        RiskAssessment newAss = RiskAssessment.builder()
                                .patient(patient)
                                .weeklyReportId(r.getId())
                                .assessmentType("WEEKLY_STROKE_RISK")
                                .riskLevel(mappedLevel)
                                .riskPercentage(finalPct)
                                .recommendation(strokeAdvice)
                                .assessedAt(LocalDateTime.now())
                                .lowConfidence(r.getAverageBloodSugar() == null)
                                .build();
                        newAss = riskAssessmentRepository.save(newAss);

                        if (!"LOW".equalsIgnoreCase(mappedLevel)) {
                            RiskWarning warning = RiskWarning.builder()
                                    .patient(patient)
                                    .riskAssessmentId(newAss.getId())
                                    .riskType("WEEKLY_STROKE_RISK")
                                    .status("new")
                                    .notified(false)
                                    .createdAt(LocalDateTime.now())
                                    .riskLevel(mappedLevel)
                                    .riskPercentage(finalPct)
                                    .message("Cảnh báo nguy cơ đột quỵ tuần: " + mappedLevel + "\nKhuyến nghị:\n• " + strokeAdvice)
                                    .build();
                            riskWarningRepository.save(warning);
                        }

                        map.put("healthStatus", mappedLevel);
                        map.put("riskPercentage", finalPct);
                        map.put("recommendation", strokeAdvice);
                        map.put("lowConfidence", newAss.getLowConfidence());
                        map.put("aiSummary", "");
                    } else {
                        map.put("healthStatus", "LOW");
                        map.put("riskPercentage", BigDecimal.ZERO);
                        map.put("recommendation", "Chưa có dữ liệu đánh giá đột quỵ cho tuần này.");
                        map.put("lowConfidence", true);
                        map.put("aiSummary", "");
                    }
                } catch (Exception ex) {
                    map.put("healthStatus", "LOW");
                    map.put("riskPercentage", BigDecimal.ZERO);
                    map.put("recommendation", "Chưa có dữ liệu đánh giá đột quỵ cho tuần này.");
                    map.put("lowConfidence", true);
                    map.put("aiSummary", "");
                }
            }
            return map;
        }).collect(Collectors.toList());

        model.addAttribute("reports", reports);
        model.addAttribute("isDoctorView", isDoctorView);
        model.addAttribute("patient", patient);
        Long targetUserId = patient.getUser() != null ? patient.getUser().getId() : null;
        model.addAttribute("userId", targetUserId != null ? targetUserId : curUserId);
        model.addAttribute("patientId", patientId);
        model.addAttribute("patientName", patient.getFullName());

        if (!reports.isEmpty()) {
            model.addAttribute("latestAssessment", reports.get(0));
            Map<String, Object> previous = reports.size() > 1 ? reports.get(1) : null;
            model.addAttribute("previousAssessment", previous);
        }

        // Custom range calculation
        if (from != null && to != null) {
            LocalDate fromDate = from;
            LocalDate toDate = to;
            if (fromDate.isAfter(toDate)) {
                LocalDate temp = fromDate;
                fromDate = toDate;
                toDate = temp;
            }

            Map<String, Object> latestRisk = dailyHealthLogService.calculateDynamicRisk(patientId, fromDate, toDate);
            List<DailyHealthLog> rangeLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetween(patientId, fromDate, toDate);

            if (latestRisk != null) {
                Map<String, Object> customRangeResult = new HashMap<>();
                customRangeResult.put("fromDate", fromDate);
                customRangeResult.put("toDate", toDate);
                customRangeResult.put("logCount", rangeLogs.size());

                String rawLevel = (String) latestRisk.get("riskLevel");
                String mappedLevel = "LOW";
                String strokeAdvice = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ đột quỵ thấp.";
                if (rawLevel != null) {
                    String lower = rawLevel.toLowerCase();
                    if (lower.contains("critical")) {
                        mappedLevel = "CRITICAL";
                        strokeAdvice = "🚨 Nguy cơ đột quỵ rất cao (Nguy hiểm)! Cần tham vấn bác sĩ ngay để kiểm soát huyết áp và các chỉ số sức khỏe.";
                    } else if (lower.contains("high")) {
                        mappedLevel = "HIGH";
                        strokeAdvice = "⚠️ Nguy cơ đột quỵ cao! Bạn nên điều chỉnh chế độ sinh hoạt, hạn chế các chất kích thích và theo dõi huyết áp thường xuyên.";
                    } else if (lower.contains("medium") || lower.contains("moderate")) {
                        mappedLevel = "MEDIUM";
                        strokeAdvice = "⚡ Nguy cơ đột quỵ ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                    }
                }

                customRangeResult.put("riskLevel", mappedLevel);
                customRangeResult.put("riskPercentage", latestRisk.get("riskPercentage"));
                customRangeResult.put("lowConfidence", rangeLogs.size() < 7);
                customRangeResult.put("recommendation", strokeAdvice);

                model.addAttribute("customRangeResult", customRangeResult);
            }
            model.addAttribute("from", fromDate);
            model.addAttribute("to", toDate);
        }

        return "healthlog/stroke-risk";
    }

    @GetMapping("/heart-risk")
    public String getHeartRisk(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long patientId,
            Model model, RedirectAttributes redirectAttributes) {
        Long curUserId = getCurrentUserId();
        if (curUserId == null) {
            return "redirect:/login";
        }

        boolean isDoctorOrAdminCaller = hasRole("ROLE_ADMIN") || hasRole("ROLE_DOCTOR");

        if (patientId == null) {
            if (userId != null) {
                Optional<Patient> pOpt = patientRepository.findByUserId(userId);
                if (pOpt.isPresent()) {
                    patientId = pOpt.get().getId();
                } else if (patientRepository.existsById(userId)) {
                    patientId = userId;
                }
            }
        }

        if (patientId == null) {
            if (isDoctorOrAdminCaller) {
                redirectAttributes.addFlashAttribute("error", "Thiếu thông tin bệnh nhân cần xem.");
                return "redirect:/health-logs/doctor-view";
            } else {
                patientId = resolvePatientId(curUserId);
            }
        }

        if (patientId == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/";
        }

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/";
        }

        if (!isDoctorOrAdminCaller) {
            Long ownPatientId = resolvePatientId(curUserId);
            if (ownPatientId == null || !ownPatientId.equals(patientId)) {
                return "redirect:/login";
            }
        } else {
            if (hasRole("ROLE_DOCTOR") && !isDoctorAssignedToPatient(patientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem báo cáo của bệnh nhân này.");
                return "redirect:/health-logs/doctor-view";
            }
        }

        boolean isDoctorView = isDoctorOrAdminCaller
                && userId != null
                && !userId.equals(curUserId);

        final Long finalPatientId = patientId;
        List<WeeklyHealthReport> dbReports = weeklyHealthReportRepository.findByPatientIdOrderByWeekStartDesc(patientId);
        List<Map<String, Object>> reports = dbReports.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("weekStart", r.getWeekStart());
            map.put("weekEnd", r.getWeekEnd());
            map.put("averageBloodSugar", r.getAverageBloodSugar());
            map.put("averageSystolic", r.getAverageSystolic());
            map.put("averageDiastolic", r.getAverageDiastolic());
            map.put("createdAt", r.getCreatedAt());

            List<DailyHealthLog> weekLogsForActivity = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(finalPatientId, r.getWeekStart(), r.getWeekEnd());
            long activeDays = weekLogsForActivity.stream().filter(l -> l.getPhysicalActivity() != null && l.getPhysicalActivity() == 1).count();
            int totalDays = weekLogsForActivity.size();
            map.put("activeDays", activeDays);
            map.put("totalDays", totalDays);
            String activeWeekLabel = "--";
            if (totalDays > 0) {
                long inactiveDays = totalDays - activeDays;
                activeWeekLabel = activeDays > inactiveDays ? "Có" : "Không";
            }
            map.put("activeWeek", activeWeekLabel);

            Optional<RiskAssessment> heartAss = riskAssessmentRepository.findByWeeklyReportIdAndAssessmentType(r.getId(), "WEEKLY_HEART_RISK");
            if (heartAss.isPresent()) {
                RiskAssessment ass = heartAss.get();
                if (ass.getRiskPercentage() == null || BigDecimal.ZERO.compareTo(ass.getRiskPercentage()) == 0) {
                    try {
                        Map<String, Object> aiHeartResult = dailyHealthLogService.calculateDynamicHeartRisk(finalPatientId, r.getWeekStart(), r.getWeekEnd());
                        if (aiHeartResult != null && !aiHeartResult.isEmpty() && !aiHeartResult.containsKey("error") && !aiHeartResult.containsKey("message")) {
                            Object heartRiskValue = aiHeartResult.containsKey("risk_percentage") ? aiHeartResult.get("risk_percentage") : aiHeartResult.get("riskPercentage");
                            Double riskPercentage = heartRiskValue != null ? Double.parseDouble(heartRiskValue.toString()) : null;
                            String rawRiskLevel = (String) aiHeartResult.get("risk_level");

                            String mappedLevel = "LOW";
                            String heartAdvice = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ tim mạch thấp.";
                            if (rawRiskLevel != null) {
                                String lowerLevel = rawRiskLevel.toLowerCase();
                                if (lowerLevel.contains("critical") || (riskPercentage != null && riskPercentage >= 75)) {
                                    mappedLevel = "CRITICAL";
                                    heartAdvice = "🚨 Nguy cơ tim mạch rất cao (Nguy hiểm)! Cần tham vấn bác sĩ ngay để kiểm soát huyết áp, đường huyết và các chỉ số sức khỏe.";
                                } else if (lowerLevel.contains("high") || (riskPercentage != null && riskPercentage >= 50)) {
                                    mappedLevel = "HIGH";
                                    heartAdvice = "⚠️ Nguy cơ tim mạch cao! Bạn nên điều chỉnh chế độ sinh hoạt, hạn chế các chất kích thích và theo dõi tim mạch thường xuyên.";
                                } else if (lowerLevel.contains("medium") || lowerLevel.contains("moderate") || (riskPercentage != null && riskPercentage >= 25)) {
                                    mappedLevel = "MEDIUM";
                                    heartAdvice = "⚡ Nguy cơ tim mạch ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                                }
                            }

                            BigDecimal finalPct = BigDecimal.valueOf(riskPercentage != null ? riskPercentage : 0.0).setScale(2, RoundingMode.HALF_UP);
                            ass.setRiskLevel(mappedLevel);
                            ass.setRiskPercentage(finalPct);
                            ass.setRecommendation(heartAdvice);
                            ass.setAiSummary("");
                            ass.setAssessedAt(LocalDateTime.now());
                            riskAssessmentRepository.save(ass);
                        }
                    } catch (Exception ex) {
                        log.warn("Không thể cập nhật lại WEEKLY_HEART_RISK từ AI động: {}", ex.getMessage());
                    }
                }

                map.put("healthStatus", ass.getRiskLevel());
                map.put("riskPercentage", ass.getRiskPercentage());
                map.put("recommendation", ass.getRecommendation());
                map.put("lowConfidence", ass.getLowConfidence());
                map.put("aiSummary", ass.getAiSummary());
            } else {
                try {
                    Map<String, Object> aiHeartResult = dailyHealthLogService.calculateDynamicHeartRisk(finalPatientId, r.getWeekStart(), r.getWeekEnd());
                    if (aiHeartResult != null && !aiHeartResult.isEmpty() && !aiHeartResult.containsKey("error") && !aiHeartResult.containsKey("message")) {
                        Object heartRiskValue = aiHeartResult.containsKey("risk_percentage") ? aiHeartResult.get("risk_percentage") : aiHeartResult.get("riskPercentage");
                        Double riskPercentage = heartRiskValue != null ? Double.parseDouble(heartRiskValue.toString()) : null;
                        String rawRiskLevel = (String) aiHeartResult.get("risk_level");

                        String mappedLevel = "LOW";
                        String heartAdvice = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ tim mạch thấp.";

                        if (rawRiskLevel != null) {
                            String lowerLevel = rawRiskLevel.toLowerCase();
                            if (lowerLevel.contains("critical") || (riskPercentage != null && riskPercentage >= 75)) {
                                mappedLevel = "CRITICAL";
                                heartAdvice = "🚨 Nguy cơ tim mạch rất cao (Nguy hiểm)! Cần tham vấn bác sĩ ngay để kiểm soát huyết áp, đường huyết và các chỉ số sức khỏe.";
                            } else if (lowerLevel.contains("high") || (riskPercentage != null && riskPercentage >= 50)) {
                                mappedLevel = "HIGH";
                                heartAdvice = "⚠️ Nguy cơ tim mạch cao! Bạn nên điều chỉnh chế độ sinh hoạt, hạn chế các chất kích thích và theo dõi tim mạch thường xuyên.";
                            } else if (lowerLevel.contains("medium") || lowerLevel.contains("moderate") || (riskPercentage != null && riskPercentage >= 25)) {
                                mappedLevel = "MEDIUM";
                                heartAdvice = "⚡ Nguy cơ tim mạch ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                            }
                        }

                        BigDecimal finalPct = BigDecimal.valueOf(riskPercentage != null ? riskPercentage : 0.0).setScale(2, RoundingMode.HALF_UP);
                        
                        RiskAssessment newAss = RiskAssessment.builder()
                                .patient(patient)
                                .weeklyReportId(r.getId())
                                .assessmentType("WEEKLY_HEART_RISK")
                                .riskLevel(mappedLevel)
                                .riskPercentage(finalPct)
                                .recommendation(heartAdvice)
                                .assessedAt(LocalDateTime.now())
                                .lowConfidence(r.getAverageBloodSugar() == null)
                                .build();
                        newAss = riskAssessmentRepository.save(newAss);

                        if (!"LOW".equalsIgnoreCase(mappedLevel)) {
                            RiskWarning warning = RiskWarning.builder()
                                    .patient(patient)
                                    .riskAssessmentId(newAss.getId())
                                    .riskType("WEEKLY_HEART_RISK")
                                    .status("new")
                                    .notified(false)
                                    .createdAt(LocalDateTime.now())
                                    .riskLevel(mappedLevel)
                                    .riskPercentage(finalPct)
                                    .message("Cảnh báo nguy cơ tim mạch tuần: " + mappedLevel + "\nKhuyến nghị:\n• " + heartAdvice)
                                    .build();
                            riskWarningRepository.save(warning);
                        }

                        map.put("healthStatus", mappedLevel);
                        map.put("riskPercentage", finalPct);
                        map.put("recommendation", heartAdvice);
                        map.put("lowConfidence", newAss.getLowConfidence());
                        map.put("aiSummary", "");
                    } else {
                        map.put("healthStatus", "LOW");
                        map.put("riskPercentage", BigDecimal.ZERO);
                        map.put("recommendation", "Chưa có dữ liệu đánh giá tim mạch cho tuần này.");
                        map.put("lowConfidence", true);
                        map.put("aiSummary", "");
                    }
                } catch (Exception ex) {
                    map.put("healthStatus", "LOW");
                    map.put("riskPercentage", BigDecimal.ZERO);
                    map.put("recommendation", "Chưa có dữ liệu đánh giá tim mạch cho tuần này.");
                    map.put("lowConfidence", true);
                    map.put("aiSummary", "");
                }
            }
            return map;
        }).collect(Collectors.toList());

        model.addAttribute("reports", reports);
        model.addAttribute("isDoctorView", isDoctorView);
        model.addAttribute("patient", patient);
        Long targetUserId = patient.getUser() != null ? patient.getUser().getId() : null;
        model.addAttribute("userId", targetUserId != null ? targetUserId : curUserId);
        model.addAttribute("patientId", patientId);
        model.addAttribute("patientName", patient.getFullName());

        if (!reports.isEmpty()) {
            model.addAttribute("latestAssessment", reports.get(0));
            Map<String, Object> previous = reports.size() > 1 ? reports.get(1) : null;
            model.addAttribute("previousAssessment", previous);
        }

        // Custom range calculation
        if (from != null && to != null) {
            LocalDate fromDate = from;
            LocalDate toDate = to;
            if (fromDate.isAfter(toDate)) {
                LocalDate temp = fromDate;
                fromDate = toDate;
                toDate = temp;
            }

            Map<String, Object> latestRisk = dailyHealthLogService.calculateDynamicHeartRisk(patientId, fromDate, toDate);
            List<DailyHealthLog> rangeLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetween(patientId, fromDate, toDate);

            if (latestRisk != null && !latestRisk.containsKey("error") && !latestRisk.containsKey("message")) {
                Map<String, Object> customRangeResult = new HashMap<>();
                customRangeResult.put("fromDate", fromDate);
                customRangeResult.put("toDate", toDate);
                customRangeResult.put("logCount", rangeLogs.size());

                String rawLevel = (String) latestRisk.get("risk_level");
                String mappedLevel = "LOW";
                String heartAdvice = "Chỉ số sức khỏe của bạn ở mức an toàn, nguy cơ tim mạch thấp.";
                if (rawLevel != null) {
                    String lower = rawLevel.toLowerCase();
                    if (lower.contains("critical")) {
                        mappedLevel = "CRITICAL";
                        heartAdvice = "🚨 Nguy cơ tim mạch rất cao (Nguy hiểm)! Cần tham vấn bác sĩ ngay để kiểm soát các chỉ số sức khỏe.";
                    } else if (lower.contains("high")) {
                        mappedLevel = "HIGH";
                        heartAdvice = "⚠️ Nguy cơ tim mạch cao! Bạn nên điều chỉnh chế độ sinh hoạt và theo dõi tim mạch thường xuyên.";
                    } else if (lower.contains("medium") || lower.contains("moderate")) {
                        mappedLevel = "MEDIUM";
                        heartAdvice = "⚡ Nguy cơ tim mạch ở mức trung bình. Hãy chú ý giữ thói quen rèn luyện thể thao đều đặn.";
                    }
                }

                customRangeResult.put("riskLevel", mappedLevel);
                Object percentageValue = latestRisk.containsKey("risk_percentage") ? latestRisk.get("risk_percentage") : latestRisk.get("riskPercentage");
                Double riskPct = percentageValue != null ? Double.parseDouble(percentageValue.toString()) : 0.0;
                customRangeResult.put("riskPercentage", BigDecimal.valueOf(riskPct).setScale(2, RoundingMode.HALF_UP));
                customRangeResult.put("lowConfidence", rangeLogs.size() < 7);
                customRangeResult.put("recommendation", heartAdvice);

                model.addAttribute("customRangeResult", customRangeResult);
            } else if (latestRisk != null && latestRisk.containsKey("message")) {
                model.addAttribute("customRangeError", latestRisk.get("message"));
            }
            model.addAttribute("from", fromDate);
            model.addAttribute("to", toDate);
        }

        return "healthlog/ai-report-heart";
    }

    private String formatDecimal(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof java.math.BigDecimal) {
            return String.format("%.2f", ((java.math.BigDecimal) obj).doubleValue());
        } else if (obj instanceof Number) {
            return String.format("%.2f", ((Number) obj).doubleValue());
        }
        return obj.toString();
    }

    private String formatMetricValue(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return String.format(Locale.US, "%.1f", value.setScale(1, RoundingMode.HALF_UP).doubleValue());
    }

    private BigDecimal calculateAverage(List<DailyHealthLog> logs, java.util.function.Function<DailyHealthLog, BigDecimal> extractor) {
        if (logs == null || logs.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (DailyHealthLog log : logs) {
            BigDecimal value = extractor.apply(log);
            if (value != null) {
                sum = sum.add(value);
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private java.math.BigDecimal getBigDecimalSafe(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) obj;
        }
        if (obj instanceof Number) {
            return java.math.BigDecimal.valueOf(((Number) obj).doubleValue());
        }
        return new java.math.BigDecimal(obj.toString());
    }

    @GetMapping("/export")
    @ResponseBody
    public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> exportExcel(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long patientId
    ) {
        Long curUserId = getCurrentUserId();
        if (curUserId == null) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        boolean isDoctorOrAdminCaller = hasRole("ROLE_ADMIN") || hasRole("ROLE_DOCTOR");

        if (patientId == null) {
            if (userId != null) {
                Optional<Patient> pOpt = patientRepository.findByUserId(userId);
                if (pOpt.isPresent()) {
                    patientId = pOpt.get().getId();
                } else if (patientRepository.existsById(userId)) {
                    patientId = userId;
                }
            }
        }

        if (patientId == null) {
            if (!isDoctorOrAdminCaller) {
                patientId = resolvePatientId(curUserId);
            }
        }

        if (patientId == null) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }

        // Quyền truy cập kiểm tra tương tự như khi xem
        if (!isDoctorOrAdminCaller) {
            Long ownPatientId = resolvePatientId(curUserId);
            if (ownPatientId == null || !ownPatientId.equals(patientId)) {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
        } else {
            if (hasRole("ROLE_DOCTOR") && !isDoctorAssignedToPatient(patientId)) {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
        }

        // Ngày mặc định nếu rỗng (lấy toàn bộ lịch sử từ năm 2000)
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : LocalDate.of(2000, 1, 1);

        java.io.ByteArrayInputStream in = exportService.exportDailyLogsToExcel(patientId, fromDate, toDate);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=nhat_ky_suc_khoe_" + patientId + ".xlsx");

        return org.springframework.http.ResponseEntity
                .ok()
                .headers(headers)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new org.springframework.core.io.InputStreamResource(in));
    }
}