package fpt.swp391.GlucoTrackAlert.controller.healthlog;

import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API phụ trợ cho trang kê đơn thuốc:
 * trả về bản ghi health log mới nhất của bệnh nhân để hiển thị
 * chỉ số và làm context cho nút gợi ý AI.
 */
@RestController
@RequestMapping("/api/health-logs")
public class HealthLogApiController {

    @Autowired
    private DailyHealthLogRepository repo;

    @GetMapping("/patient/{patientId}/latest")
    public ResponseEntity<Map<String, Object>> getLatest(@PathVariable Long patientId) {
        var page = repo.findByPatientIdOrderByLogDateDesc(patientId, PageRequest.of(0, 1));
        Map<String, Object> result = new LinkedHashMap<>();

        if (page.isEmpty()) {
            result.put("found", false);
            return ResponseEntity.ok(result);
        }

        DailyHealthLog log = page.getContent().get(0);
        Patient patient = log.getPatient();

        result.put("found", true);
        result.put("logDate", log.getLogDate() != null ? log.getLogDate().toString() : null);
        result.put("bloodSugar", log.getBloodSugar());
        result.put("systolic", log.getSystolic());
        result.put("diastolic", log.getDiastolic());
        result.put("symptoms", log.getSymptoms());
        result.put("note", log.getNote());
        result.put("patientAge", patient != null ? patient.getAge() : null);
        result.put("patientGender", patient != null ? patient.getGender() : null);
        result.put("patientBmi", patient != null ? patient.getBmi() : null);

        return ResponseEntity.ok(result);
    }
}