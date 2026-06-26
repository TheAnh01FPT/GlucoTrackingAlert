package fpt.swp391.GlucoTrackAlert.controller.healthlog;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogRequest;
import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.model.Doctor;
import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import fpt.swp391.GlucoTrackAlert.repository.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.DoctorPatientAssignmentRepository;
import java.util.Collections;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import fpt.swp391.GlucoTrackAlert.service.DailyHealthLogService;
import lombok.RequiredArgsConstructor;
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
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/health-logs")
@RequiredArgsConstructor
public class DailyHealthLogController {

    private final DailyHealthLogService dailyHealthLogService;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorPatientAssignmentRepository assignmentRepository;
    private final JdbcTemplate jdbcTemplate;

    private Long resolvePatientId(Long userId) {
        if (userId == null) {
            return null;
        }
        // Resolve by treating the parameter as a user ID and finding the linked
        // patient.
        // Do NOT treat the parameter as a patient ID to avoid ambiguous collisions
        // between
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

    private boolean checkOwnership(Long logPatientId) {
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
            Model model) {
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
            List<DoctorPatientAssignment> assignments = assignmentRepository.findByDoctorIdAndStatus(doctor.getId(),
                    "active");
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
                // ignore
            }

            // Get the latest weekly AI prediction for this patient
            List<Map<String, Object>> riskList = jdbcTemplate.queryForList(
                    "SELECT ra.id, ra.risk_percentage, ra.risk_level, ra.ai_summary, ra.recommendation, ra.assessed_at "
                            +
                            "FROM risk_assessments ra " +
                            "WHERE ra.patient_id = ? AND ra.assessment_type = 'WEEKLY_AI_PREDICTION' " +
                            "ORDER BY ra.assessed_at DESC LIMIT 1",
                    selectedPatientId);

            Map<String, Object> latestRisk = null;
            if (!riskList.isEmpty()) {
                Map<String, Object> raw = riskList.get(0);
                latestRisk = new HashMap<>();
                latestRisk.put("id", raw.get("id"));

                Object pct = raw.get("risk_percentage");
                if (pct instanceof java.math.BigDecimal) {
                    latestRisk.put("riskPercentage", String.format("%.2f", ((java.math.BigDecimal) pct).doubleValue()));
                } else if (pct instanceof Number) {
                    latestRisk.put("riskPercentage", String.format("%.2f", ((Number) pct).doubleValue()));
                } else {
                    latestRisk.put("riskPercentage", pct != null ? pct.toString() : "0.00");
                }

                latestRisk.put("riskLevel", raw.get("risk_level"));
                latestRisk.put("aiSummary", raw.get("ai_summary"));
                latestRisk.put("recommendation", raw.get("recommendation"));

                Object assessedAtObj = raw.get("assessed_at");
                if (assessedAtObj != null) {
                    if (assessedAtObj instanceof java.time.LocalDateTime) {
                        java.time.LocalDateTime ldt = (java.time.LocalDateTime) assessedAtObj;
                        latestRisk.put("assessedAtStr",
                                ldt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                    } else if (assessedAtObj instanceof java.util.Date) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
                        latestRisk.put("assessedAtStr", sdf.format((java.util.Date) assessedAtObj));
                    } else {
                        latestRisk.put("assessedAtStr", assessedAtObj.toString());
                    }
                }
            }
            model.addAttribute("latestRisk", latestRisk);

            Pageable pageable = PageRequest.of(page, size);
            Page<DailyHealthLogResponse> logsPage = dailyHealthLogService.getLogs(selectedPatientId, pageable);
            model.addAttribute("logs", logsPage.getContent());
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
        Pageable pageable = PageRequest.of(page, size);
        Page<DailyHealthLogResponse> logsPage = dailyHealthLogService.getLogs(patientId, pageable);
        model.addAttribute("logs", logsPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logsPage.getTotalPages());
        model.addAttribute("totalElements", logsPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("userId", userId);
        model.addAttribute("patientId", patientId);
        return "healthlog/patient-logs";
    }

    @GetMapping("/detail")
    public String getLogById(@RequestParam Long logId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String source,
            Model model,
            RedirectAttributes redirectAttributes) {
        DailyHealthLogResponse log = dailyHealthLogService.getLogById(logId);
        if (log == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy nhật ký");
            return "redirect:/health-logs";
        }
        if (!checkOwnership(log.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền truy cập nhật ký này");
            Long curUserId = getCurrentUserId();
            if (curUserId == null) {
                curUserId = userId;
            }
            return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : "");
        }

        // Chỉ ADMIN hoặc doctor được phân công mới xem được
        if (hasRole("ROLE_DOCTOR") || hasRole("ROLE_ADMIN")) {
            if (!isDoctorAssignedToPatient(log.getPatientId())) {
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem nhật ký này");
                return "redirect:/health-logs/doctor-view";
            }
        }
        model.addAttribute("log", log);
        model.addAttribute("source", source);
        return "healthlog/detail";
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

        return assignmentRepository.findByDoctorIdAndPatientId(doctor.getId(), patientId).isPresent();
    }

    @GetMapping("/create")
    public String createLogForm(@RequestParam Long userId,
            @RequestParam(required = false) String source,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!hasRole("ROLE_ADMIN")) {
            if (hasRole("ROLE_DOCTOR")) {
                Long patientId = resolvePatientId(userId);
                if (patientId == null || !isDoctorAssignedToPatient(patientId)) {
                    redirectAttributes.addFlashAttribute("error", "Bạn không được phân công quản lý bệnh nhân này.");
                    return "redirect:/health-logs/doctor-view";
                }
            } else {
                Long curUserId = getCurrentUserId();
                if (curUserId == null || !curUserId.equals(userId)) {
                    redirectAttributes.addFlashAttribute("error",
                            "Bạn không có quyền tạo nhật ký cho người dùng khác.");
                    return "redirect:/login";
                }
            }
        }

        if (!model.containsAttribute("log")) {
            DailyHealthLogRequest request = new DailyHealthLogRequest();
            request.setLogDate(LocalDate.now());
            model.addAttribute("log", request);
        }
        model.addAttribute("userId", userId);
        model.addAttribute("source", source);
        model.addAttribute("action",
                "/health-logs/create?userId=" + userId + "&source=" + (source != null ? source : "my-logs"));
        return "healthlog/form";
    }

    @PostMapping("/create")
    public String createLog(@RequestParam Long userId,
            @RequestParam(required = false) String source,
            @Valid @ModelAttribute("log") DailyHealthLogRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (!hasRole("ROLE_ADMIN")) {
            if (hasRole("ROLE_DOCTOR")) {
                Long patientId = resolvePatientId(userId);
                if (patientId == null || !isDoctorAssignedToPatient(patientId)) {
                    redirectAttributes.addFlashAttribute("error", "Bạn không được phân công quản lý bệnh nhân này.");
                    return "redirect:/health-logs/doctor-view";
                }
            } else {
                Long curUserId = getCurrentUserId();
                if (curUserId == null || !curUserId.equals(userId)) {
                    redirectAttributes.addFlashAttribute("error",
                            "Bạn không có quyền tạo nhật ký cho người dùng khác.");
                    return "redirect:/login";
                }
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

        dailyHealthLogService.createLog(patientId, request);
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
        if (!checkOwnership(response.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền truy cập nhật ký này");
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
        if (!checkOwnership(response.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền truy cập nhật ký này");
            Long curUserId = getCurrentUserId();
            if ("my-logs".equals(source)) {
                return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : userId);
            }
            return "redirect:/health-logs?userId=" + (curUserId != null ? curUserId : userId);
        }

        dailyHealthLogService.updateLog(id, request);
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
        if (!checkOwnership(response.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền truy cập nhật ký này");
            Long curUserId = getCurrentUserId();
            return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : userId);
        }

        dailyHealthLogService.deleteLog(id);
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
            Model model) {
        List<Patient> patients = patientRepository.findAllByStatus("active");
        if (patients.isEmpty()) {
            patients = patientRepository.findAll();
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
            Model model) {
        Long patientId = resolvePatientId(userId);
        if (patientId == null) {
            patientId = userId;
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
            Model model) {
        // Chỉ DOCTOR/ADMIN mới vào được
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            return "redirect:/login";
        }

        List<Patient> patients = patientRepository.findAllByStatus("active");
        if (patients.isEmpty()) {
            patients = patientRepository.findAll();
        }
        model.addAttribute("patients", patients);

        Long selectedPatientId = userId;
        if (selectedPatientId == null && !patients.isEmpty()) {
            selectedPatientId = patients.get(0).getId();
        }

        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(30);

        if (selectedPatientId != null) {
            List<DailyHealthLogResponse> chartData = dailyHealthLogService.getChartData(selectedPatientId, startDate,
                    endDate);
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
            @RequestParam(required = false) Long assessmentId,
            Model model, RedirectAttributes redirectAttributes) {
        Long curUserId = getCurrentUserId();
        if (curUserId == null) {
            return "redirect:/login";
        }

        Long targetUserId = userId;
        if (targetUserId == null) {
            targetUserId = curUserId;
        }

        // Access check
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            if (!curUserId.equals(targetUserId)) {
                return "redirect:/login";
            }
        }

        Long patientId = resolvePatientId(targetUserId);
        if (patientId == null) {
            // Fallback: Check if targetUserId is actually a patientId
            if (targetUserId != null && patientRepository.existsById(targetUserId)) {
                patientId = targetUserId;
                Patient p = patientRepository.findById(patientId).orElse(null);
                if (p != null && p.getUser() != null) {
                    targetUserId = p.getUser().getId();
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
                return "redirect:/";
            }
        }

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/";
        }

        if (hasRole("ROLE_DOCTOR") || hasRole("ROLE_ADMIN")) {
            if (!isDoctorAssignedToPatient(patientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem báo cáo của bệnh nhân này.");
                return "redirect:/health-logs/doctor-view";
            }
        }

        // Trigger weekly AI prediction calculation (On-Demand)
        dailyHealthLogService.assessWeeklyRisk(patientId);

        // Get all weekly assessments for this patient
        List<Map<String, Object>> allAssessmentsRaw = jdbcTemplate.queryForList(
                "SELECT ra.id, ra.assessed_at, dhl.log_date FROM risk_assessments ra " +
                        "LEFT JOIN daily_health_logs dhl ON ra.daily_health_log_id = dhl.id " +
                        "WHERE ra.patient_id = ? AND ra.assessment_type = 'WEEKLY_AI_PREDICTION' " +
                        "ORDER BY ra.assessed_at DESC",
                patientId);

        List<Map<String, Object>> allAssessments = new java.util.ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (Map<String, Object> raw : allAssessmentsRaw) {
            Long idVal = ((Number) raw.get("id")).longValue();
            Object assessedAtObj = raw.get("assessed_at");
            LocalDateTime ldt = null;
            if (assessedAtObj instanceof LocalDateTime) {
                ldt = (LocalDateTime) assessedAtObj;
            } else if (assessedAtObj instanceof java.sql.Timestamp) {
                ldt = ((java.sql.Timestamp) assessedAtObj).toLocalDateTime();
            }

            Object logDateObj = raw.get("log_date");
            LocalDate logDate = null;
            if (logDateObj instanceof LocalDate) {
                logDate = (LocalDate) logDateObj;
            } else if (logDateObj instanceof java.sql.Date) {
                logDate = ((java.sql.Date) logDateObj).toLocalDate();
            } else if (logDateObj instanceof java.util.Date) {
                logDate = new java.sql.Date(((java.util.Date) logDateObj).getTime()).toLocalDate();
            }

            String label = "Không rõ ngày";
            if (logDate != null) {
                LocalDate start = logDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate end = logDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

                LocalDate assessDate = LocalDate.now();
                if (ldt != null) {
                    assessDate = ldt.toLocalDate();
                }
                label = String.format("Tuần %s - %s (Đánh giá: %s)", start.format(formatter), end.format(formatter),
                        assessDate.format(formatter));
            }
            Map<String, Object> option = new HashMap<>();
            option.put("id", idVal);
            option.put("label", label);
            allAssessments.add(option);
        }

        // Determine which assessment to display
        Long selectedId = assessmentId;
        if (selectedId == null && !allAssessments.isEmpty()) {
            selectedId = (Long) allAssessments.get(0).get("id");
        }

        // Get the specific weekly AI risk assessment
        List<Map<String, Object>> list = Collections.emptyList();
        if (selectedId != null) {
            list = jdbcTemplate.queryForList(
                    "SELECT ra.risk_percentage, ra.risk_level, ra.ai_summary, ra.recommendation, ra.assessed_at, dhl.blood_sugar, dhl.systolic, dhl.diastolic, dhl.log_date, "
                            +
                            "       whr.average_blood_sugar, whr.average_systolic, whr.average_diastolic, whr.average_sleep_hours, whr.average_water_ml, "
                            +
                            "       whr.high_sugar_days, whr.warning_count, whr.blood_sugar_change, whr.blood_sugar_change_percent, "
                            +
                            "       whr.systolic_change, whr.diastolic_change, whr.sleep_hours_change, whr.trend_status, whr.health_status, whr.week_start "
                            +
                            "FROM risk_assessments ra " +
                            "LEFT JOIN daily_health_logs dhl ON ra.daily_health_log_id = dhl.id " +
                            "LEFT JOIN weekly_health_reports whr ON whr.patient_id = ra.patient_id AND dhl.log_date >= whr.week_start AND dhl.log_date <= whr.week_end "
                            +
                            "WHERE ra.id = ? AND ra.patient_id = ?",
                    selectedId, patientId);
        }

        Map<String, Object> latestRisk = null;
        if (!list.isEmpty()) {
            Map<String, Object> raw = list.get(0);
            latestRisk = new HashMap<>();

            // Safe decimal conversion
            Object pct = raw.get("risk_percentage");
            if (pct instanceof java.math.BigDecimal) {
                latestRisk.put("riskPercentage", String.format("%.2f", ((java.math.BigDecimal) pct).doubleValue()));
            } else if (pct instanceof Number) {
                latestRisk.put("riskPercentage", String.format("%.2f", ((Number) pct).doubleValue()));
            } else {
                latestRisk.put("riskPercentage", pct != null ? pct.toString() : "0.00");
            }

            latestRisk.put("riskLevel", raw.get("risk_level"));
            latestRisk.put("aiSummary", raw.get("ai_summary"));
            latestRisk.put("recommendation", raw.get("recommendation"));

            // Safe date formatting in Java
            Object assessedAtObj = raw.get("assessed_at");
            if (assessedAtObj != null) {
                if (assessedAtObj instanceof java.time.LocalDateTime) {
                    java.time.LocalDateTime ldt = (java.time.LocalDateTime) assessedAtObj;
                    latestRisk.put("assessedAtStr",
                            ldt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                } else if (assessedAtObj instanceof java.util.Date) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
                    latestRisk.put("assessedAtStr", sdf.format((java.util.Date) assessedAtObj));
                } else {
                    latestRisk.put("assessedAtStr", assessedAtObj.toString());
                }
            }

            Object logDateObj = raw.get("log_date");
            if (logDateObj != null) {
                if (logDateObj instanceof java.time.LocalDate) {
                    java.time.LocalDate ld = (java.time.LocalDate) logDateObj;
                    latestRisk.put("logDateStr", ld.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                } else if (logDateObj instanceof java.sql.Date) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
                    latestRisk.put("logDateStr", sdf.format((java.sql.Date) logDateObj));
                } else if (logDateObj instanceof java.util.Date) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
                    latestRisk.put("logDateStr", sdf.format((java.util.Date) logDateObj));
                } else {
                    latestRisk.put("logDateStr", logDateObj.toString());
                }
            }

            // Weekly health reports mapping
            latestRisk.put("avgSugar", formatDecimal(raw.get("average_blood_sugar")));
            latestRisk.put("avgSystolic", formatDecimal(raw.get("average_systolic")));
            latestRisk.put("avgDiastolic", formatDecimal(raw.get("average_diastolic")));
            latestRisk.put("avgSleep", formatDecimal(raw.get("average_sleep_hours")));
            latestRisk.put("avgWater", formatDecimal(raw.get("average_water_ml")));

            latestRisk.put("highSugarDays", raw.get("high_sugar_days"));
            latestRisk.put("warningCount", raw.get("warning_count"));

            latestRisk.put("sugarChange", formatDecimal(raw.get("blood_sugar_change")));
            latestRisk.put("sugarChangePercent", formatDecimal(raw.get("blood_sugar_change_percent")));
            latestRisk.put("systolicChange", formatDecimal(raw.get("systolic_change")));
            latestRisk.put("diastolicChange", formatDecimal(raw.get("diastolic_change")));
            latestRisk.put("sleepChange", formatDecimal(raw.get("sleep_hours_change")));

            latestRisk.put("trendStatus", raw.get("trend_status"));
            latestRisk.put("healthStatus", raw.get("health_status"));
            latestRisk.put("waterChange", null); // Initialize default to prevent SpelEvaluationException

            // Calculate water change on the fly since there's no water_change column
            Object weekStartObj = raw.get("week_start");
            LocalDate weekStart = null;
            if (weekStartObj instanceof LocalDate) {
                weekStart = (LocalDate) weekStartObj;
            } else if (weekStartObj instanceof java.sql.Date) {
                weekStart = ((java.sql.Date) weekStartObj).toLocalDate();
            } else if (weekStartObj instanceof java.util.Date) {
                weekStart = new java.sql.Date(((java.util.Date) weekStartObj).getTime()).toLocalDate();
            }

            if (weekStart != null) {
                LocalDate prevStart = weekStart.minusWeeks(1);
                List<Map<String, Object>> prevList = jdbcTemplate.queryForList(
                        "SELECT average_water_ml FROM weekly_health_reports WHERE patient_id = ? AND week_start = ?",
                        patientId, prevStart);
                if (!prevList.isEmpty() && raw.get("average_water_ml") != null) {
                    java.math.BigDecimal currentWater = getBigDecimalSafe(raw.get("average_water_ml"));
                    java.math.BigDecimal prevWater = getBigDecimalSafe(prevList.get(0).get("average_water_ml"));
                    if (prevWater != null) {
                        java.math.BigDecimal waterChangeVal = currentWater.subtract(prevWater);
                        latestRisk.put("waterChange", formatDecimal(waterChangeVal));
                    }
                }
            }
        }

        boolean isDocOrAdmin = hasRole("ROLE_DOCTOR") || hasRole("ROLE_ADMIN");
        model.addAttribute("isDoctorOrAdmin", isDocOrAdmin);

        model.addAttribute("patient", patient);
        model.addAttribute("userId", targetUserId);
        model.addAttribute("latestRisk", latestRisk);
        model.addAttribute("hasRiskData", latestRisk != null);
        model.addAttribute("allAssessments", allAssessments);
        model.addAttribute("selectedAssessmentId", selectedId);

        return "healthlog/ai-report";
    }

    private String formatDecimal(Object obj) {
        if (obj == null)
            return null;
        if (obj instanceof java.math.BigDecimal) {
            return String.format("%.2f", ((java.math.BigDecimal) obj).doubleValue());
        } else if (obj instanceof Number) {
            return String.format("%.2f", ((Number) obj).doubleValue());
        }
        return obj.toString();
    }

    private java.math.BigDecimal getBigDecimalSafe(Object obj) {
        if (obj == null)
            return null;
        if (obj instanceof java.math.BigDecimal)
            return (java.math.BigDecimal) obj;
        if (obj instanceof Number)
            return java.math.BigDecimal.valueOf(((Number) obj).doubleValue());
        return new java.math.BigDecimal(obj.toString());
    }
}
