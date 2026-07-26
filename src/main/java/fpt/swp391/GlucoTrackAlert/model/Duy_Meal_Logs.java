package fpt.swp391.GlucoTrackAlert.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

@Entity
@Table(name = "meal_logs")
public class Duy_Meal_Logs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

<<<<<<< HEAD
    @Column(name = "user_id", nullable = false)
    private Long patientId;

    @NotBlank(message = "Ten mon an khong duoc de trong")
    @Column(name = "food_name", nullable = false)
    private String foodName;

    // So luong bua an do benh nhan nhap (vd: 1 bat, 2 mieng, nua to...)
    @Column(name = "quantity_text")
    private String quantityText;

    // Luong duong huyet uoc tinh sau bua an (mmol/L) - he thong tu tinh
    @Column(name = "sugar_estimation")
    private Double sugarEstimation;

    @Column(name = "meal_type")
    private String mealType;

    @Column(name = "note")
=======
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotBlank(message = "Mo ta mon an khong duoc de trong")
    @Column(name = "food_name", nullable = false, columnDefinition = "TEXT")
    private String foodName;  // ✅ giữ tên Java cũ để frontend không phải đổi

    @Column(name = "meal_type")
    private String mealType;  // ✅ giữ tên Java cũ

    @Column(name = "quantity_text")
    private String quantityText;  // Số lượng người dùng nhập, vd: "1 bát", "2 miếng"

    @Column(name = "sugar_estimation")
    private String sugarEstimation;  // Đường huyết ước tính sau ăn, đơn vị mmol/L (String thay vì Double - giữ tên Java cũ)

    @Column(name = "carb_estimation")
    private Double carbEstimation;  // Khối lượng carbohydrate ước tính, đơn vị gram (g)

    @Column(name = "note", columnDefinition = "TEXT")
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
    private String note;

    @Column(name = "log_date", nullable = false)
    private LocalDate mealDate;

<<<<<<< HEAD
    // --- LOGIC ---
    public boolean isHighSugar() {
        return sugarEstimation != null && sugarEstimation > 7.8;
    }

    public boolean isDangerSugar() {
        return sugarEstimation != null && sugarEstimation >= 11.0;
    }

    // --- CONSTRUCTORS ---
    public Duy_Meal_Logs() {}

    // --- GETTERS & SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public String getQuantityText() { return quantityText; }
    public void setQuantityText(String quantityText) { this.quantityText = quantityText; }

    public Double getSugarEstimation() { return sugarEstimation; }
    public void setSugarEstimation(Double sugarEstimation) { this.sugarEstimation = sugarEstimation; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDate getMealDate() { return mealDate; }
    public void setMealDate(LocalDate mealDate) { this.mealDate = mealDate; }

    // --- NORMALIZE DATA ---
    @PrePersist
    @PreUpdate
    public void normalizeData() {
        if (foodName != null) foodName = foodName.trim();
        if (quantityText != null) quantityText = quantityText.trim();
    // Không ép sugarEstimation về 0.0 — để null nếu chưa có dữ liệu,
    // tránh ghi đè giá trị hợp lệ khi update
    if (mealDate == null) mealDate = LocalDate.now();
    }
}
=======
    // --- CONSTRUCTORS ---
    public Duy_Meal_Logs() {
    }

    // --- GETTERS & SETTERS ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public String getQuantityText() {
        return quantityText;
    }

    public void setQuantityText(String quantityText) {
        this.quantityText = quantityText;
    }

    public String getSugarEstimation() {
        return sugarEstimation;
    }

    public void setSugarEstimation(String sugarEstimation) {
        this.sugarEstimation = sugarEstimation;
    }

    public Double getCarbEstimation() {
        return carbEstimation;
    }

    public void setCarbEstimation(Double carbEstimation) {
        this.carbEstimation = carbEstimation;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDate getMealDate() {
        return mealDate;
    }

    public void setMealDate(LocalDate mealDate) {
        this.mealDate = mealDate;
    }

    @PrePersist
    @PreUpdate
    public void normalizeData() {
        if (foodName != null) {
            foodName = foodName.trim();
        }
        if (mealDate == null) {
            mealDate = LocalDate.now();
        }
    }
}
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
