package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.model.Duy_Meal_Logs;
import fpt.swp391.GlucoTrackAlert.repository.Duy_MealLogRepository;
import fpt.swp391.GlucoTrackAlert.service.Duy_MealLogService;
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
                    if (log.getFoodName() != null) existing.setFoodName(log.getFoodName());
                    if (log.getQuantityText() != null) existing.setQuantityText(log.getQuantityText());
                    if (log.getSugarEstimation() != null) existing.setSugarEstimation(log.getSugarEstimation());
                    if (log.getMealType() != null) existing.setMealType(log.getMealType());
                    if (log.getNote() != null) existing.setNote(log.getNote());
                    if (log.getMealDate() != null) existing.setMealDate(log.getMealDate());
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
        // Tinh tong luong duong uoc tinh cua tat ca bua an trong ngay theo patientId
        return repo.findByPatientId(patientId).stream()
                .mapToDouble(x -> x.getSugarEstimation() == null ? 0.0 : x.getSugarEstimation())
                .sum();
    }

    @Override
    public Double calculateAvgSugarForUser(Long patientId) {
        // Tinh trung binh duong uoc tinh theo patientId
        return repo.findByPatientId(patientId).stream()
                .filter(x -> x.getSugarEstimation() != null && x.getSugarEstimation() > 0)
                .mapToDouble(Duy_Meal_Logs::getSugarEstimation)
                .average()
                .orElse(0.0);
    }

    @Override
    public List<Duy_Meal_Logs> getHighSugarMeals() {
        // Nguong hoi cao: >= 7.8 mmol/L
        return repo.findBySugarEstimationGreaterThan(7.8);
    }

    @Override
    public List<Duy_Meal_Logs> getDangerSugarMeals() {
        // Nguong nguy hiem: >= 11.0 mmol/L
        return repo.findBySugarEstimationGreaterThan(11.0);
    }

    @Override
    public List<Duy_Meal_Logs> getLogsByPatient(Long patientId) {
        return repo.findByPatientId(patientId);
    }

   
}