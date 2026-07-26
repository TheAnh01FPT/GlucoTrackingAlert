package fpt.swp391.GlucoTrackAlert.service.meallog;

import fpt.swp391.GlucoTrackAlert.model.meallog.Duy_Meal_Logs;

import java.util.List;
import java.util.Optional;

public interface Duy_MealLogService {

    Duy_Meal_Logs save(Duy_Meal_Logs log);

    List<Duy_Meal_Logs> getAllLogs();

    Optional<Duy_Meal_Logs> getLogById(Long id);

    Duy_Meal_Logs updateLog(Long id, Duy_Meal_Logs log);

    void deleteLog(Long id);

    // Tinh tong duong uoc tinh theo benh nhan
    Double calculateTotalSugarForUser(Long patientId);

    // Tinh trung binh duong uoc tinh theo benh nhan
    Double calculateAvgSugarForUser(Long patientId);

    // Lay cac bua an duong hoi cao (>= 7.8 mmol/L)
    List<Duy_Meal_Logs> getHighSugarMeals();

    // Lay cac bua an duong nguy hiem (>= 11.0 mmol/L)
    List<Duy_Meal_Logs> getDangerSugarMeals();

    List<Duy_Meal_Logs> getLogsByPatient(Long patientId);
}