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

    // FIX 1: Đổi tên cột từ "user_id" → "patient_id" cho đúng với bảng patients
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotBlank(message = "Ten mon an khong duoc de trong")
    @Column(name = "food_name", nullable = false)
    private String foodName;

    @Column(name = "quantity_text")
    private String quantityText;

    @Column(name = "sugar_estimation")
    private Double sugarEstimation;

    @Column(name = "meal_type")
    private String mealType;

    @Column(name = "note")
    private String note;

    @Column(name = "log_date", nullable = false)
    private LocalDate mealDate;

    // --- LOGIC ---
    // FIX 2: Thống nhất ngưỡng đường huyết với Service và Controller
    // Hơi cao (post-meal): > 7.8 mmol/L
    public boolean isHighSugar() {
        return sugarEstimation != null && sugarEstimation > 7.8;
    }

    // Nguy hiểm: >= 11.0 mmol/L
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
        if (mealDate == null) mealDate = LocalDate.now();
    }
}