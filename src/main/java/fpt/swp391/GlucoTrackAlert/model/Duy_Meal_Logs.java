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

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @NotBlank(message = "Mo ta mon an khong duoc de trong")
    @Column(name = "food_description", nullable = false, columnDefinition = "TEXT")
    private String foodName;  // ✅ giữ tên Java cũ để frontend không phải đổi

    @Column(name = "meal_time")
    private String mealType;  // ✅ giữ tên Java cũ

    @Column(name = "sugar_level")
    private String sugarEstimation;  // ✅ giữ tên Java cũ (String thay vì Double)

    @Column(name = "carb_estimation")
    private Double carbEstimation;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    // --- CONSTRUCTORS ---
    public Duy_Meal_Logs() {}

    // --- GETTERS & SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public String getSugarEstimation() { return sugarEstimation; }
    public void setSugarEstimation(String sugarEstimation) { this.sugarEstimation = sugarEstimation; }

    public Double getCarbEstimation() { return carbEstimation; }
    public void setCarbEstimation(Double carbEstimation) { this.carbEstimation = carbEstimation; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDate getMealDate() { return mealDate; }
    public void setMealDate(LocalDate mealDate) { this.mealDate = mealDate; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    @PreUpdate
    public void normalizeData() {
        if (foodName != null) foodName = foodName.trim();
        if (mealDate == null) mealDate = LocalDate.now();
    }
}