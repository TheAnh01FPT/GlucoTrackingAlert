package fpt.swp391.GlucoTrackAlert.controller.meallog;

import fpt.swp391.GlucoTrackAlert.model.meallog.Duy_Meal_Logs;
import fpt.swp391.GlucoTrackAlert.service.meallog.Duy_MealLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meal-logs")
<<<<<<< HEAD
// FIX 6: Không dùng @CrossOrigin("*") chung chung trong production.
// Giữ tạm để dev, nhưng nên chỉ định origin cụ thể khi deploy.
@CrossOrigin(origins = "*")
=======
// FIX 3: Bỏ @CrossOrigin("*") vì Security đã xử lý, tránh lộ API
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
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

<<<<<<< HEAD
    // READ ALL
    @GetMapping
=======
    // READ ALL - chỉ dành cho ADMIN
    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
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
<<<<<<< HEAD
            @RequestBody Duy_Meal_Logs log) {
        try {
            Duy_Meal_Logs updated = mealLogService.updateLog(id, log);
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
=======
                                    @RequestBody Duy_Meal_Logs log) {
        Duy_Meal_Logs updated = mealLogService.updateLog(id, log);
        if (updated == null) {
<<<<<<< HEAD
            // FIX 6: Trả 404 thay vì 400 khi không tìm thấy
=======
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
            return ResponseEntity.notFound().build();
>>>>>>> 216d1c80e6cc9d94add0215ea117711f338cb8d2
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

<<<<<<< HEAD
   

    // REPORT - HIGH SUGAR (> 10 mmol/L)
=======
    // REPORT - HIGH SUGAR (> 7.8 mmol/L — hơi cao sau bữa ăn)
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
    @GetMapping("/report/high-sugar")
    public ResponseEntity<List<Duy_Meal_Logs>> highSugar() {
        return ResponseEntity.ok(mealLogService.getHighSugarMeals());
    }

<<<<<<< HEAD
    // REPORT - DANGER SUGAR (> 13.9 mmol/L)
=======
    // REPORT - DANGER SUGAR (>= 11.0 mmol/L — nguy hiểm)
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
    @GetMapping("/report/danger-sugar")
    public ResponseEntity<List<Duy_Meal_Logs>> dangerSugar() {
        return ResponseEntity.ok(mealLogService.getDangerSugarMeals());
    }
<<<<<<< HEAD
}
=======
<<<<<<< HEAD
}
=======
}
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
>>>>>>> 216d1c80e6cc9d94add0215ea117711f338cb8d2
