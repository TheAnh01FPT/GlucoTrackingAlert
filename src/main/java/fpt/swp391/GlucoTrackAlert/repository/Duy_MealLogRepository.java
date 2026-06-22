package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.Duy_Meal_Logs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface Duy_MealLogRepository extends JpaRepository<Duy_Meal_Logs, Long> {

    List<Duy_Meal_Logs> findByPatientId(Long patientId);

    List<Duy_Meal_Logs> findBySugarEstimationGreaterThan(Double value);

    List<Duy_Meal_Logs> findByMealType(String mealType);

    List<Duy_Meal_Logs> findByFoodNameContainingIgnoreCase(String foodName);
}