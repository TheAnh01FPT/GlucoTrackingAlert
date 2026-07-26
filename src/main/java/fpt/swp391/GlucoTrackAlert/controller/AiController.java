package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.dto.healthlog.DailyHealthLogResponse;
import fpt.swp391.GlucoTrackAlert.model.doctor.Doctor;
import fpt.swp391.GlucoTrackAlert.model.Duy_Meal_Logs;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.doctor.DoctorRepository;
import fpt.swp391.GlucoTrackAlert.repository.Duy_MealLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.DailyHealthLogService;
import fpt.swp391.GlucoTrackAlert.service.MlAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final MlAnalysisService mlAnalysisService;
    private final DailyHealthLogService dailyHealthLogService;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final Duy_MealLogRepository mealLogRepository; // ✅ thêm

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestParam Long patientId, Principal principal) {

        if (principal != null) {
            Doctor doctor = doctorRepository.findByUserEmail(principal.getName()).orElse(null);
            if (doctor != null && !"active".equals(doctor.getStatus())) {
                return ResponseEntity.status(403).body(
                    Map.of("error", "Tài khoản bác sĩ chưa được kích hoạt. Vui lòng hoàn tất xác minh hồ sơ.")
                );
            }
        }

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy bệnh nhân"));
        }

        List<DailyHealthLogResponse> logs = dailyHealthLogService
                .getLogs(patientId, PageRequest.of(0, 14))
                .getContent();

        if (logs.isEmpty()) {
            return ResponseEntity.ok(Map.of("result", "⚠️ Bệnh nhân chưa có dữ liệu sức khỏe nào để phân tích."));
        }

        // ✅ Fetch meal logs cùng khoảng thời gian với health logs
        LocalDate newest = logs.get(0).getLogDate();
        LocalDate oldest = logs.get(logs.size() - 1).getLogDate();

        List<Duy_Meal_Logs> meals = mealLogRepository
                .findByPatientIdAndMealDateBetweenOrderByMealDateAsc(patientId, oldest, newest);

        Map<LocalDate, List<Duy_Meal_Logs>> mealsByDate = meals.stream()
                .collect(Collectors.groupingBy(Duy_Meal_Logs::getMealDate));

        String result = mlAnalysisService.analyzePatient(patient, logs, mealsByDate);
        return ResponseEntity.ok(Map.of("result", result));
    }
}