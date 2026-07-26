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
        validateFoodName(log.getFoodName());
        validateQuantityText(log.getQuantityText());
        validateSugarEstimation(log.getSugarEstimation());
        validateCarbEstimation(log.getCarbEstimation());
        return repo.save(log);
    }

    // FIX: trước đây các validate này chỉ tồn tại ở JavaScript phía form
    // (meal-logs.html) — ai gọi thẳng API (Postman, sửa request bằng
    // DevTools...) thì bỏ qua được hết, lưu thoải mái foodName rỗng,
    // quantityText/sugarEstimation âm hoặc rác. Validate lại ở đây để
    // không phụ thuộc hoàn toàn vào client.
    private void validateFoodName(String foodName) {
        if (foodName == null || foodName.isBlank()) {
            throw new IllegalArgumentException("Ten mon an khong duoc de trong");
        }
    }

    // FIX: form meal-logs.html dùng 1 ô nhập tự do duy nhất cho "số lượng + đơn vị"
    // (vd "2 bát", "150g") — validate lại đúng logic này ở backend để không ai gọi
    // thẳng API mà né được: lấy token số đầu tiên trong chuỗi, số đó phải là số
    // nguyên dương thuần túy (không âm, không thập phân, không chữ/ký tự đặc biệt
    // lẫn vào phần số), và phần còn lại sau khi bỏ số đi (đơn vị) bắt buộc phải có,
    // không giới hạn danh sách vì người dùng được tự nhập (vd "phần", "tô lớn"...).
    private void validateQuantityText(String quantityText) {
        if (quantityText == null || quantityText.isBlank()) {
            throw new IllegalArgumentException("So luong khong duoc de trong");
        }
        String trimmed = quantityText.trim();
        var numMatcher = java.util.regex.Pattern.compile("(-?\\d+\\.?\\d*)").matcher(trimmed);
        if (!numMatcher.find()) {
            throw new IllegalArgumentException("So luong phai co chua so luong, vi du '2 bat'");
        }
        String numToken = numMatcher.group(1);
        if (!numToken.matches("\\d+")) {
            throw new IllegalArgumentException(
                    "So luong chi duoc nhap so nguyen duong, khong duoc so am hoac so thap phan");
        }
        long n = Long.parseLong(numToken);
        if (n <= 0) {
            throw new IllegalArgumentException("So luong phai lon hon 0");
        }
        String unit = trimmed.replaceFirst(java.util.regex.Pattern.quote(numToken), "")
                .replaceAll("^[\\s,./-]+|[\\s,./-]+$", "").trim();
        if (unit.isEmpty()) {
            throw new IllegalArgumentException(
                    "Phai nhap don vi (vi du 'bat', 'mieng', 'thia'...), khong duoc de trong");
        }
    }

    private void validateSugarEstimation(String sugarEstimation) {
        if (sugarEstimation == null || sugarEstimation.isBlank()) {
            return;
        }
        try {
            if (Double.parseDouble(sugarEstimation.trim()) < 0) {
                throw new IllegalArgumentException("Duong huyet uoc tinh khong duoc am");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Duong huyet uoc tinh khong hop le");
        }
    }

    // FIX: carbEstimation < 0 vẫn lọt qua được ở cả save() lẫn updateLog(),
    // vì trước đó chỉ check != null chứ không check giá trị âm.
    private void validateCarbEstimation(Double carbEstimation) {
        if (carbEstimation != null && carbEstimation < 0) {
            throw new IllegalArgumentException("carbEstimation khong duoc am");
        }
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
                    if (log.getFoodName() != null) {
                        validateFoodName(log.getFoodName());
                        existing.setFoodName(log.getFoodName());
                    }
                    if (log.getMealType() != null) {
                        existing.setMealType(log.getMealType());
                    }
                    if (log.getQuantityText() != null) {
                        validateQuantityText(log.getQuantityText());
                        existing.setQuantityText(log.getQuantityText());
                    }
                    if (log.getSugarEstimation() != null) {
                        validateSugarEstimation(log.getSugarEstimation());
                        existing.setSugarEstimation(log.getSugarEstimation());
                    }
                    if (log.getCarbEstimation() != null) {
                        validateCarbEstimation(log.getCarbEstimation());
                        existing.setCarbEstimation(log.getCarbEstimation());
                    }
                    if (log.getNote() != null) {
                        existing.setNote(log.getNote());
                    }
                    if (log.getMealDate() != null) {
                        existing.setMealDate(log.getMealDate());
                    }
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
        // LƯU Ý: tính tổng CARB (gram) nạp vào trong các bữa, KHÔNG PHẢI tổng đường huyết (mmol/L).
        // Đường huyết là chỉ số đo tại một thời điểm, cộng dồn nhiều lần đo lại với nhau
        // là vô nghĩa về mặt y khoa, nên carb (có thể cộng dồn) mới là đại lượng đúng để tính "tổng".
        // Giữ nguyên tên hàm/API cũ (report/total-sugar) để không phá vỡ các chỗ đang gọi.
        return repo.findByPatientId(patientId).stream()
                .mapToDouble(x -> x.getCarbEstimation() == null ? 0.0 : x.getCarbEstimation())
                .sum();
    }

    @Override
    public Double calculateAvgSugarForUser(Long patientId) {
        // Tương tự: trung bình CARB (gram) mỗi bữa, không phải trung bình mmol/L.
        return repo.findByPatientId(patientId).stream()
                .filter(x -> x.getCarbEstimation() != null && x.getCarbEstimation() > 0)
                .mapToDouble(Duy_Meal_Logs::getCarbEstimation)
                .average()
                .orElse(0.0);
    }

    @Override
    public List<Duy_Meal_Logs> getHighSugarMeals() {
        // Ngưỡng cảnh báo áp dụng cho ĐƯỜNG HUYẾT ước tính (mmol/L), không phải carb (g).
        // Trước đây code này lọc theo carbEstimation (gram carb) bằng ngưỡng của mmol/L,
        // nên gần như MỌI bữa có tinh bột đều bị tính nhầm là "cao đường huyết".
        return repo.findAll().stream()
                .filter(x -> parseSugar(x.getSugarEstimation()) > 7.8)
                .toList();
    }

    @Override
    public List<Duy_Meal_Logs> getDangerSugarMeals() {
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
    }

    @Override
    public List<Duy_Meal_Logs> getLogsByPatient(Long patientId) {
        return repo.findByPatientId(patientId);
    }
}
