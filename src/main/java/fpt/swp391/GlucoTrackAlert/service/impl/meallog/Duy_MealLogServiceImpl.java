package fpt.swp391.GlucoTrackAlert.service.impl.meallog;

import fpt.swp391.GlucoTrackAlert.model.meallog.Duy_Meal_Logs;
import fpt.swp391.GlucoTrackAlert.repository.meallog.Duy_MealLogRepository;
import fpt.swp391.GlucoTrackAlert.service.meallog.Duy_MealLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class Duy_MealLogServiceImpl implements Duy_MealLogService {

    @Autowired
    private Duy_MealLogRepository repo;

    @Override
    public Duy_Meal_Logs save(Duy_Meal_Logs log) {
        return repo.save(log);
    }

    @Override
    public List<Duy_Meal_Logs> getAllLogs() {
        return repo.findAll();
    }

    @Override
    public Optional<Duy_Meal_Logs> getLogById(Long id) {
        return repo.findById(id);
    }

    @Override
    public Duy_Meal_Logs updateLog(Long id, Duy_Meal_Logs log) {
        return repo.findById(id)
                .map(existing -> {
<<<<<<< HEAD
                    if (log.getFoodName() != null) existing.setFoodName(log.getFoodName());
                    if (log.getQuantityText() != null) existing.setQuantityText(log.getQuantityText());
                    if (log.getSugarEstimation() != null) existing.setSugarEstimation(log.getSugarEstimation());
                    if (log.getMealType() != null) existing.setMealType(log.getMealType());
                    if (log.getNote() != null) existing.setNote(log.getNote());
                    if (log.getMealDate() != null) existing.setMealDate(log.getMealDate());
=======
                    if (log.getFoodName() != null) {
                        existing.setFoodName(log.getFoodName());
                    }
                    if (log.getMealType() != null) {
                        existing.setMealType(log.getMealType());
                    }
                    if (log.getQuantityText() != null) {
                        existing.setQuantityText(log.getQuantityText());
                    }
                    if (log.getSugarEstimation() != null) {
                        existing.setSugarEstimation(log.getSugarEstimation());
                    }
                    if (log.getCarbEstimation() != null) {
                        existing.setCarbEstimation(log.getCarbEstimation());
                    }
                    if (log.getNote() != null) {
                        existing.setNote(log.getNote());
                    }
                    if (log.getMealDate() != null) {
                        existing.setMealDate(log.getMealDate());
                    }
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
                    return repo.save(existing);
                })
                .orElse(null);
    }

    @Override
    public void deleteLog(Long id) {
        if (!repo.existsById(id)) {
            throw new IllegalArgumentException("Meal log khong ton tai voi id: " + id);
        }
        repo.deleteById(id);
    }

    @Override
    public Double calculateTotalSugarForUser(Long patientId) {
<<<<<<< HEAD
        // Tinh tong luong duong uoc tinh cua tat ca bua an trong ngay theo patientId
        return repo.findByPatientId(patientId).stream()
                .mapToDouble(x -> x.getSugarEstimation() == null ? 0.0 : x.getSugarEstimation())
=======
        // LƯU Ý: tính tổng CARB (gram) nạp vào trong các bữa, KHÔNG PHẢI tổng đường huyết (mmol/L).
        // Đường huyết là chỉ số đo tại một thời điểm, cộng dồn nhiều lần đo lại với nhau
        // là vô nghĩa về mặt y khoa, nên carb (có thể cộng dồn) mới là đại lượng đúng để tính "tổng".
        // Giữ nguyên tên hàm/API cũ (report/total-sugar) để không phá vỡ các chỗ đang gọi.
        return repo.findByPatientId(patientId).stream()
                .mapToDouble(x -> x.getCarbEstimation() == null ? 0.0 : x.getCarbEstimation())
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
                .sum();
    }

    @Override
    public Double calculateAvgSugarForUser(Long patientId) {
<<<<<<< HEAD
        // Tinh trung binh duong uoc tinh theo patientId
        return repo.findByPatientId(patientId).stream()
                .filter(x -> x.getSugarEstimation() != null && x.getSugarEstimation() > 0)
                .mapToDouble(Duy_Meal_Logs::getSugarEstimation)
=======
        // Tương tự: trung bình CARB (gram) mỗi bữa, không phải trung bình mmol/L.
        return repo.findByPatientId(patientId).stream()
                .filter(x -> x.getCarbEstimation() != null && x.getCarbEstimation() > 0)
                .mapToDouble(Duy_Meal_Logs::getCarbEstimation)
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
                .average()
                .orElse(0.0);
    }

    @Override
    public List<Duy_Meal_Logs> getHighSugarMeals() {
<<<<<<< HEAD
        // Nguong hoi cao: >= 7.8 mmol/L
        return repo.findBySugarEstimationGreaterThan(7.8);
=======
        // Ngưỡng cảnh báo áp dụng cho ĐƯỜNG HUYẾT ước tính (mmol/L), không phải carb (g).
        // Trước đây code này lọc theo carbEstimation (gram carb) bằng ngưỡng của mmol/L,
        // nên gần như MỌI bữa có tinh bột đều bị tính nhầm là "cao đường huyết".
        return repo.findAll().stream()
                .filter(x -> parseSugar(x.getSugarEstimation()) > 7.8)
                .toList();
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
    }

    @Override
    public List<Duy_Meal_Logs> getDangerSugarMeals() {
<<<<<<< HEAD
        // Nguong nguy hiem: >= 11.0 mmol/L
        return repo.findBySugarEstimationGreaterThan(11.0);
=======
        // Nguong nguy hiem: >= 11.0 mmol/L (WHO/ADA), lọc trên sugarEstimation thực tế
        return repo.findAll().stream()
                .filter(x -> parseSugar(x.getSugarEstimation()) >= 11.0)
                .toList();
    }

    // sugarEstimation được lưu dạng String (giữ nguyên kiểu cũ để không phải đổi schema DB),
    // nên cần parse an toàn: giá trị rỗng/không hợp lệ được coi là 0 thay vì làm crash cả API.
    private double parseSugar(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
    }

    @Override
    public List<Duy_Meal_Logs> getLogsByPatient(Long patientId) {
        return repo.findByPatientId(patientId);
    }
<<<<<<< HEAD

   
}
=======
}
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
