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
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
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
import java.time.YearMonth;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
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
    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final JdbcTemplate jdbcTemplate;

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
            List<DoctorPatientAssignment> assignments =
                    assignmentRepository.findByDoctorIdAndStatus(doctor.getId(), "active");
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
                // ignore
            }

            // Get the latest weekly AI prediction for this patient
            List<Map<String, Object>> riskList = jdbcTemplate.queryForList(
                    "SELECT ra.id, ra.risk_percentage, ra.risk_level, ra.ai_summary, ra.recommendation, ra.assessed_at " +
                            "FROM risk_assessments ra " +
                            "WHERE ra.patient_id = ? AND ra.assessment_type = 'WEEKLY_AI_PREDICTION' " +
                            "ORDER BY ra.assessed_at DESC LIMIT 1",
                    selectedPatientId
            );

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
        model.addAttribute("latestLog", dailyHealthLogRepository.findFirstByPatientIdOrderByLogDateDesc(patientId));
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
                    redirectAttributes.addFlashAttribute("error", "Bạn không có quyền tạo nhật ký cho người dùng khác.");
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
            if (hasRole("ROLE_DOCTOR")) {
                Long patientId = resolvePatientId(userId);
                if (patientId == null || !isDoctorAssignedToPatient(patientId)) {
                    redirectAttributes.addFlashAttribute("error", "Bạn không được phân công quản lý bệnh nhân này.");
                    return "redirect:/health-logs/doctor-view";
                }
            } else {
                Long curUserId = getCurrentUserId();
                if (curUserId == null || !curUserId.equals(userId)) {
                    redirectAttributes.addFlashAttribute("error", "Bạn không có quyền tạo nhật ký cho người dùng khác.");
                    return "redirect:/login";
                }
            }
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.log", bindingResult);
            redirectAttributes.addFlashAttribute("log", request);
            String redirectUrl = "redirect:/health-logs/create?userId=" + userId;
            if ("my-logs".equals(source)) redirectUrl += "&source=my-logs";
            else if ("doctor-view".equals(source)) redirectUrl += "&source=doctor-view";
            return redirectUrl;
        }

        Long patientId = resolvePatientId(userId);
        if (patientId == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Không tìm thấy thông tin bệnh nhân tương ứng với ID: " + userId);
            if ("my-logs".equals(source)) return "redirect:/health-logs/my-logs?userId=" + userId;
            return "redirect:/health-logs?userId=" + userId;
        }

        try {
            dailyHealthLogService.createLog(patientId, request);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("log", request);
            String redirectUrl = "redirect:/health-logs/create?userId=" + userId;
            if ("my-logs".equals(source)) redirectUrl += "&source=my-logs";
            else if ("doctor-view".equals(source)) redirectUrl += "&source=doctor-view";
            return redirectUrl;
        }

        if ("my-logs".equals(source)) return "redirect:/health-logs/my-logs?userId=" + userId;
        else if ("doctor-view".equals(source)) return "redirect:/health-logs/doctor-view?userId=" + userId;
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
        if (!checkOwnership(response.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền truy cập nhật ký này");
            Long curUserId = getCurrentUserId();
            if ("my-logs".equals(source)) {
                return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : userId);
            }
            return "redirect:/health-logs?userId=" + (curUserId != null ? curUserId : userId);
        }

        try {
            dailyHealthLogService.updateLog(id, request);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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
        if (!checkOwnership(response.getPatientId())) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền truy cập nhật ký này");
            Long curUserId = getCurrentUserId();
            return "redirect:/health-logs/my-logs?userId=" + (curUserId != null ? curUserId : userId);
        }

        try {
            dailyHealthLogService.deleteLog(id);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
                    ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            redirectAttributes.addFlashAttribute("error", msg != null ? msg : "Không thể xóa nhật ký do ràng buộc dữ liệu.");
            if ("my-logs".equals(source)) {
                return "redirect:/health-logs/my-logs?userId=" + userId;
            } else if ("doctor-view".equals(source)) {
                return "redirect:/health-logs/doctor-view?userId=" + userId;
            }
            return "redirect:/health-logs?userId=" + userId;
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Không thể xóa nhật ký.";
            redirectAttributes.addFlashAttribute("error", msg);
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
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        // Chỉ DOCTOR/ADMIN mới vào được
        if (!hasRole("ROLE_ADMIN") && !hasRole("ROLE_DOCTOR")) {
            return "redirect:/login";
        }
        List<Patient> patients;
        if (hasRole("ROLE_DOCTOR")) {
            Long currentUserId = getCurrentUserId();
            Doctor doctor = doctorRepository.findByUserId(currentUserId).orElse(null);
            if (doctor == null) {
                return "redirect:/health-logs/doctor-view";
            }
            List<DoctorPatientAssignment> assignments = assignmentRepository.findByDoctorIdAndStatus(doctor.getId(), "active");
            patients = assignments.stream().map(DoctorPatientAssignment::getPatient).collect(Collectors.toList());
        } else {
            patients = patientRepository.findAllByStatus("active");
            if (patients.isEmpty()) patients = patientRepository.findAll();
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

        YearMonth previousMonth = selectedMonth.minusMonths(1);
        List<DailyHealthLog> previousMonthLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetween(patientId, previousMonth.atDay(1), previousMonth.atEndOfMonth());
        BigDecimal previousAvgSugar = calculateAverage(previousMonthLogs, log -> log.getBloodSugar());

        String monthlyProgressStatus = "STABLE";
        String monthlyProgressLabel = "Đường huyết ổn định so với tháng trước.";
        if (monthlyAvgSugar != null && previousAvgSugar != null) {
            BigDecimal delta = monthlyAvgSugar.subtract(previousAvgSugar);
            if (delta.compareTo(new BigDecimal("0.3")) <= -1) {
                monthlyProgressStatus = "IMPROVING";
                monthlyProgressLabel = "Đường huyết cải thiện rõ rệt so với tháng trước.";
            } else if (delta.compareTo(new BigDecimal("0.3")) >= 1) {
                monthlyProgressStatus = "WORSENING";
                monthlyProgressLabel = "Đường huyết có xu hướng xấu đi so với tháng trước.";
            }
        }

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
        model.addAttribute("monthlyProgressStatus", monthlyProgressStatus);
        model.addAttribute("monthlyProgressLabel", monthlyProgressLabel);
        model.addAttribute("monthOptions", monthOptions);
        model.addAttribute("monthlyAiEvaluation", null);
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

        // Dynamic AI prediction calculation
        LocalDate toDate = to;
        LocalDate fromDate = from;
        if (toDate == null || fromDate == null) {
            Page<DailyHealthLogResponse> latestLogs = dailyHealthLogService.getLogs(patientId, PageRequest.of(0, 1));
            if (latestLogs != null && latestLogs.hasContent()) {
                toDate = latestLogs.getContent().get(0).getLogDate();
            } else {
                toDate = LocalDate.now();
            }
            fromDate = toDate.minusDays(6);
        }

        if (fromDate.isAfter(toDate)) {
            LocalDate temp = fromDate;
            fromDate = toDate;
            toDate = temp;
        }

        Map<String, Object> latestRisk = dailyHealthLogService.calculateDynamicRisk(patientId, fromDate, toDate);

        // Fetch detailed logs in the range to display on the explanation table
        List<DailyHealthLog> rangeLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetween(patientId, fromDate, toDate);
        rangeLogs.sort(java.util.Comparator.comparing(DailyHealthLog::getLogDate).reversed());

        boolean isDocOrAdmin = hasRole("ROLE_DOCTOR") || hasRole("ROLE_ADMIN");
        model.addAttribute("isDoctorOrAdmin", isDocOrAdmin);
        model.addAttribute("patient", patient);
        Long targetUserId = patient.getUser() != null ? patient.getUser().getId() : null;
        model.addAttribute("userId", targetUserId != null ? targetUserId : curUserId);
        model.addAttribute("patientId", patientId);
        model.addAttribute("latestRisk", latestRisk);
        model.addAttribute("hasRiskData", latestRisk != null);
        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("rangeLogs", rangeLogs);

        return "healthlog/stroke-risk";
    }
    @GetMapping("/heart-risk")
    public String getHeartRisk(@RequestParam(required = false) Long userId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) Long patientId,
                               Model model, RedirectAttributes redirectAttributes) {
        Long curUserId = getCurrentUserId();
        if (curUserId == null) return "redirect:/login";

        boolean isDoctorOrAdminCaller = hasRole("ROLE_ADMIN") || hasRole("ROLE_DOCTOR");

        // 1. Phân giải PatientId từ userId hoặc ngược lại
        if (patientId == null && userId != null) {
            Optional<Patient> pOpt = patientRepository.findByUserId(userId);
            if (pOpt.isPresent()) patientId = pOpt.get().getId();
            else if (patientRepository.existsById(userId)) patientId = userId;
        }
        if (patientId == null) {
            patientId = isDoctorOrAdminCaller ? null : resolvePatientId(curUserId);
        }
        if (patientId == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return isDoctorOrAdminCaller ? "redirect:/health-logs/doctor-view" : "redirect:/";
        }

        // 2. Kiểm tra quyền truy cập (Access Control)
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy hồ sơ bệnh nhân.");
            return "redirect:/";
        }
        if (!isDoctorOrAdminCaller) {
            Long ownPatientId = resolvePatientId(curUserId);
            if (ownPatientId == null || !ownPatientId.equals(patientId)) return "redirect:/login";
        } else {
            if (hasRole("ROLE_DOCTOR") && !isDoctorAssignedToPatient(patientId)) {
                redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xem báo cáo của bệnh nhân này.");
                return "redirect:/health-logs/doctor-view";
            }
        }

        // 3. Xử lý khoảng ngày đo dữ liệu (Mặc định 14 ngày gần nhất để phân tích tim mạch tốt hơn)
        LocalDate toDate = to;
        LocalDate fromDate = from;
        if (toDate == null || fromDate == null) {
            toDate = LocalDate.now();
            fromDate = toDate.minusDays(14); // Thu thập dữ liệu trong 14 ngày
        }
        if (fromDate.isAfter(toDate)) {
            LocalDate temp = fromDate; fromDate = toDate; toDate = temp;
        }

        // 4. Gọi Python AI thông qua Service xử lý động
        Map<String, Object> heartRiskData = dailyHealthLogService.calculateDynamicHeartRisk(patientId, fromDate, toDate);

        // 5. Bổ sung dữ liệu hiển thị thời gian phân tích 'assessedAtStr' cho phù hợp cấu trúc giao diện ai-report-heart.html
        if (heartRiskData != null && !heartRiskData.containsKey("error") && !heartRiskData.containsKey("message")) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            heartRiskData.put("assessedAtStr", now.format(formatter));

            // Ép key từ gạch dưới của Flask thành CamelCase để khớp với `ai-report-heart.html` cũ
            if (heartRiskData.containsKey("risk_percentage")) {
                heartRiskData.put("riskPercentage", formatDecimal(heartRiskData.get("risk_percentage")));
            }
            if (heartRiskData.containsKey("risk_level")) {
                heartRiskData.put("riskLevel", heartRiskData.get("risk_level"));
            }

            model.addAttribute("latestRisk", heartRiskData);
            model.addAttribute("hasRiskData", true);
        } else {
            model.addAttribute("hasRiskData", false);
            if (heartRiskData != null && heartRiskData.containsKey("message")) {
                model.addAttribute("aiMessage", heartRiskData.get("message"));
            }
        }

        // 6. Đổ danh sách log trong khoảng ngày ra bảng giải trình dữ liệu
        List<DailyHealthLog> rangeLogs = dailyHealthLogRepository.findByPatientIdAndLogDateBetween(patientId, fromDate, toDate);
        rangeLogs.sort(java.util.Comparator.comparing(DailyHealthLog::getLogDate).reversed());

        model.addAttribute("isDoctorOrAdmin", isDoctorOrAdminCaller);
        model.addAttribute("patient", patient);
        Long targetUserId = patient.getUser() != null ? patient.getUser().getId() : null;
        model.addAttribute("userId", targetUserId != null ? targetUserId : curUserId);
        model.addAttribute("patientId", patientId);
        model.addAttribute("from", fromDate);
        model.addAttribute("to", toDate);
        model.addAttribute("rangeLogs", rangeLogs);

        return "healthlog/ai-report-heart"; // Render file HTML tim mạch của bạn
    }

    private String formatDecimal(Object obj) {
        if (obj == null) return null;
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
        if (obj == null) return null;
        if (obj instanceof java.math.BigDecimal) return (java.math.BigDecimal) obj;
        if (obj instanceof Number) return java.math.BigDecimal.valueOf(((Number) obj).doubleValue());
        return new java.math.BigDecimal(obj.toString());
    }


}


