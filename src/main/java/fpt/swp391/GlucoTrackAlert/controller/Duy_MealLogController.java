package fpt.swp391.GlucoTrackAlert.controller;

import fpt.swp391.GlucoTrackAlert.model.Duy_Meal_Logs;
import fpt.swp391.GlucoTrackAlert.service.Duy_MealLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meal-logs")
// FIX 3: Bỏ @CrossOrigin("*") vì Security đã xử lý, tránh lộ API
public class Duy_MealLogController {

    @Autowired
    private Duy_MealLogService mealLogService;

    // CREATE
    @PostMapping
    public ResponseEntity<?> addLog(@RequestBody Duy_Meal_Logs log) {
        if (log.getPatientId() == null) {
            return ResponseEntity.badRequest().body("Error: patientId is required!");
        }
        try {
            Duy_Meal_Logs savedLog = mealLogService.save(log);
            return ResponseEntity.ok(savedLog);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // READ ALL - chỉ dành cho ADMIN
    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Duy_Meal_Logs>> getAll() {
        return ResponseEntity.ok(mealLogService.getAllLogs());
    }

    // READ BY PATIENT
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<Duy_Meal_Logs>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(mealLogService.getLogsByPatient(patientId));
    }

    // READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return mealLogService.getLogById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
            @RequestBody Duy_Meal_Logs log) {
        try {
            Duy_Meal_Logs updated = mealLogService.updateLog(id, log);
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            mealLogService.deleteLog(id);
            return ResponseEntity.ok("Deleted ID: " + id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // REPORT - TOTAL SUGAR theo bệnh nhân
    @GetMapping("/report/total-sugar")
    public ResponseEntity<?> totalSugar(@RequestParam Long patientId) {
        return ResponseEntity.ok(mealLogService.calculateTotalSugarForUser(patientId));
    }

    // REPORT - HIGH SUGAR (> 7.8 mmol/L — hơi cao sau bữa ăn)
    @GetMapping("/report/high-sugar")
    public ResponseEntity<List<Duy_Meal_Logs>> highSugar() {
        return ResponseEntity.ok(mealLogService.getHighSugarMeals());
    }

    // REPORT - DANGER SUGAR (>= 11.0 mmol/L — nguy hiểm)
    @GetMapping("/report/danger-sugar")
    public ResponseEntity<List<Duy_Meal_Logs>> dangerSugar() {
        return ResponseEntity.ok(mealLogService.getDangerSugarMeals());
    }
}
