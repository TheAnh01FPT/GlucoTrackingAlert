package fpt.swp391.GlucoTrackAlert.repository.meallog;

import fpt.swp391.GlucoTrackAlert.model.meallog.Duy_Meal_Logs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

<<<<<<< HEAD
=======
import java.time.LocalDate;
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
import java.util.List;

@Repository
public interface Duy_MealLogRepository extends JpaRepository<Duy_Meal_Logs, Long> {

    List<Duy_Meal_Logs> findByPatientId(Long patientId);

<<<<<<< HEAD
    List<Duy_Meal_Logs> findBySugarEstimationGreaterThan(Double value);

    List<Duy_Meal_Logs> findByMealType(String mealType);

    List<Duy_Meal_Logs> findByFoodNameContainingIgnoreCase(String foodName);
}
=======
    List<Duy_Meal_Logs> findByMealType(String mealType);

    List<Duy_Meal_Logs> findByFoodNameContainingIgnoreCase(String foodName);

    List<Duy_Meal_Logs> findByPatientIdAndMealDateBetweenOrderByMealDateAsc(
            Long patientId, LocalDate from, LocalDate to);
}
>>>>>>> c609fe15c2adf12d4f6fbd628b23773040d3f6f7
