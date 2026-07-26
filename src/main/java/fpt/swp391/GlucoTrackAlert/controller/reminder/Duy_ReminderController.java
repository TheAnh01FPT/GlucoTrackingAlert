package fpt.swp391.GlucoTrackAlert.controller.reminder;

import fpt.swp391.GlucoTrackAlert.dto.reminder.Duy_ReminderRequest;
import fpt.swp391.GlucoTrackAlert.dto.reminder.Duy_ReminderResponse;
import fpt.swp391.GlucoTrackAlert.service.reminder.Duy_ReminderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
public class Duy_ReminderController {

    @Autowired
    private Duy_ReminderService reminderService;

    // ==================== CRUD ====================

    /** CREATE - Tạo nhắc nhở mới (optionally sync GG Calendar nếu có token) */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody Duy_ReminderRequest request) {
        if (request.getPatientId() == null) {
            return ResponseEntity.badRequest().body("Error: patientId là bắt buộc");
        }
        Duy_ReminderResponse saved = reminderService.create(request);
        return ResponseEntity.ok(saved);
    }

    /** READ ALL by patient */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<Duy_ReminderResponse>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(reminderService.getAllByPatient(patientId));
    }

    /** READ ONE by id */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return reminderService.getById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** UPDATE */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Duy_ReminderRequest request) {
        try {
            Duy_ReminderResponse updated = reminderService.update(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** DELETE */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            reminderService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Đã xoá nhắc nhở ID: " + id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== LỌC ====================

    /** Lấy theo status: ACTIVE / COMPLETED / CANCELLED */
    @GetMapping("/patient/{patientId}/status/{status}")
    public ResponseEntity<List<Duy_ReminderResponse>> getByStatus(
            @PathVariable Long patientId, @PathVariable String status) {
        return ResponseEntity.ok(reminderService.getByPatientAndStatus(patientId, status));
    }

    /** Lấy theo type: MEDICATION, BLOOD_SUGAR, MEAL, EXERCISE, DOCTOR_VISIT, CUSTOM */
    @GetMapping("/patient/{patientId}/type/{type}")
    public ResponseEntity<List<Duy_ReminderResponse>> getByType(
            @PathVariable Long patientId, @PathVariable String type) {
        return ResponseEntity.ok(reminderService.getByPatientAndType(patientId, type));
    }

    /** Sắp tới trong 24h */
    @GetMapping("/patient/{patientId}/upcoming")
    public ResponseEntity<List<Duy_ReminderResponse>> getUpcoming(@PathVariable Long patientId) {
        return ResponseEntity.ok(reminderService.getUpcoming(patientId));
    }

    /** Đếm reminder active */
    @GetMapping("/patient/{patientId}/count-active")
    public ResponseEntity<?> countActive(@PathVariable Long patientId) {
        long count = reminderService.countActive(patientId);
        return ResponseEntity.ok(Map.of("activeCount", count));
    }

    // ==================== HÀNH ĐỘNG NHANH ====================

    /** Đánh dấu hoàn thành (giống tick trong iPhone Reminders) */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(reminderService.markComplete(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Khôi phục về ACTIVE */
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<?> reactivate(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(reminderService.markActive(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== GOOGLE CALENDAR ====================

    /**
     * Đồng bộ reminder lên Google Calendar
     * Body: { "googleAccessToken": "ya29.xxx..." }
     */
    @PostMapping("/{id}/sync-google")
    public ResponseEntity<?> syncGoogle(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        String token = body.get("googleAccessToken");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("googleAccessToken là bắt buộc");
        }
        try {
            Duy_ReminderResponse res = reminderService.syncToGoogleCalendar(id, token);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Lỗi đồng bộ Google Calendar: " + e.getMessage());
        }
    }

    /**
     * Xoá event khỏi Google Calendar
     * Body: { "googleAccessToken": "ya29.xxx..." }
     */
    @DeleteMapping("/{id}/unsync-google")
    public ResponseEntity<?> unsyncGoogle(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        String token = body.get("googleAccessToken");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("googleAccessToken là bắt buộc");
        }
        try {
            reminderService.deleteFromGoogleCalendar(id, token);
            return ResponseEntity.ok(Map.of("message", "Đã xoá khỏi Google Calendar"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Lỗi xoá Google Calendar: " + e.getMessage());
        }
    }
}

