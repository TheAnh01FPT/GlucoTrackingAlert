package fpt.swp391.GlucoTrackAlert.controller.healthlog;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogRequest;
import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/health-logs")
@RequiredArgsConstructor
public class DailyHealthLogController {

    private final DailyHealthLogService dailyHealthLogService;
    private final PatientRepository patientRepository;

    private Long resolvePatientId(Long userId) {
        if (userId == null) {
            return null;
        }
        // Try to find patient by user ID first
        Optional<Patient> patientOpt = patientRepository.findByUserId(userId);
        if (patientOpt.isPresent()) {
            return patientOpt.get().getId();
        }
        // Fallback to checking if patient ID itself was passed
        if (patientRepository.existsById(userId)) {
            return userId;
        }
        return null;
    }

    @GetMapping
    public String getLogs(@RequestParam(required = false) Long userId,
            @RequestParam(required = false) String patientType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
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

        Long selectedPatientId = resolvePatientId(userId);
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

        Long selectedPatientId = resolvePatientId(userId);
        if (selectedPatientId == null && !patients.isEmpty()) {
            selectedPatientId = patients.get(0).getId();
        }

        Patient selectedPatient = null;
        if (selectedPatientId != null) {
            Optional<Patient> patientOpt = patientRepository.findById(selectedPatientId);
            if (patientOpt.isPresent()) {
                selectedPatient = patientOpt.get();
            }

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

        model.addAttribute("selectedPatient", selectedPatient);
        return "healthlog/doctor-view";
    }

    @GetMapping("/my-logs")
    public String getMyLogs(@RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Long patientId = resolvePatientId(userId);
        if (patientId == null) {
            patientId = userId;
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<DailyHealthLogResponse> logsPage = dailyHealthLogService.getLogs(patientId, pageable);
        model.addAttribute("logs", logsPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logsPage.getTotalPages());
        model.addAttribute("totalElements", logsPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("userId", userId);
        return "healthlog/patient-logs";
    }

    @GetMapping("/{id}")
    public String getLogById(@PathVariable Long id, Model model) {
        DailyHealthLogResponse log = dailyHealthLogService.getLogById(id);
        model.addAttribute("log", log);
        return "healthlog/detail";
    }

    @GetMapping("/create")
    public String createLogForm(@RequestParam Long userId, Model model) {
        DailyHealthLogRequest request = new DailyHealthLogRequest();
        request.setLogDate(LocalDate.now());
        model.addAttribute("log", request);
        model.addAttribute("userId", userId);
        model.addAttribute("action", "/health-logs/create?userId=" + userId);
        return "healthlog/form";
    }

    @PostMapping("/create")
    public String createLog(@RequestParam Long userId,
            @RequestParam(required = false) String source,
            @Valid @ModelAttribute("log") DailyHealthLogRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.log", bindingResult);
            redirectAttributes.addFlashAttribute("log", request);
            return "redirect:/health-logs/create?userId=" + userId;
        }

        Long patientId = resolvePatientId(userId);
        if (patientId == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy thông tin bệnh nhân tương ứng với ID: " + userId);
            return "redirect:/health-logs?userId=" + userId;
        }

        dailyHealthLogService.createLog(patientId, request);
        if ("my-logs".equals(source)) {
            return "redirect:/health-logs/my-logs?userId=" + userId;
        }
        return "redirect:/health-logs?userId=" + userId;
    }

    @GetMapping("/{id}/edit")
    public String editLogForm(@PathVariable Long id, Model model) {
        DailyHealthLogResponse response = dailyHealthLogService.getLogById(id);

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
        model.addAttribute("userId", response.getPatientId());
        model.addAttribute("action", "/health-logs/" + id + "/edit?userId=" + response.getPatientId());
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
            return "redirect:/health-logs/" + id + "/edit?userId=" + userId;
        }

        dailyHealthLogService.updateLog(id, request);
        if ("my-logs".equals(source)) {
            return "redirect:/health-logs/my-logs?userId=" + userId;
        }
        return "redirect:/health-logs?userId=" + userId;
    }

    @PostMapping("/{id}/delete")
    public String deleteLog(@PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam(required = false) String source) {
        dailyHealthLogService.deleteLog(id);
        if ("my-logs".equals(source)) {
            return "redirect:/health-logs/my-logs?userId=" + userId;
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
}
