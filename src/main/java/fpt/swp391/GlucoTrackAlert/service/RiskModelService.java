package fpt.swp391.GlucoTrackAlert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fpt.swp391.GlucoTrackAlert.enums.RiskLevel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RiskModelService {

    private double[] scalerMean;
    private double[] scalerScale;
    private double[] coefficients;
    private double intercept;

    @PostConstruct
    public void loadModel() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new ClassPathResource("ai-CKD-model/ckd-risk-model.json").getInputStream());
            scalerMean = toDoubleArray(root.get("scaler_mean"));
            scalerScale = toDoubleArray(root.get("scaler_scale"));
            coefficients = toDoubleArray(root.get("coefficients"));
            intercept = root.get("intercept").asDouble();
            log.info("CKD model loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load CKD model", e);
        }
    }

    private double[] toDoubleArray(JsonNode node) {
        double[] arr = new double[node.size()];
        for (int i = 0; i < node.size(); i++) arr[i] = node.get(i).asDouble();
        return arr;
    }

    public double predictRiskPercentage(double age, double diastolic, double bloodSugar, double hypertensionScore) {
        // Convert blood sugar from mmol/L (app) to mg/dL (model training units)
        double bloodSugarMgDl = bloodSugar * 18.0182;

        // Clamp inputs to training dataset realistic ranges and warn when clamping occurs
        double clampedAge = age;
        if (clampedAge < 2) {
            log.warn("Age {} below model min, clamping to 2", age);
            clampedAge = 2;
        } else if (clampedAge > 90) {
            log.warn("Age {} above model max, clamping to 90", age);
            clampedAge = 90;
        }

        double clampedDiastolic = diastolic;
        if (clampedDiastolic < 50) {
            log.warn("Diastolic {} below model min, clamping to 50", diastolic);
            clampedDiastolic = 50;
        } else if (clampedDiastolic > 180) {
            log.warn("Diastolic {} above model max, clamping to 180", diastolic);
            clampedDiastolic = 180;
        }

        double clampedBloodSugar = bloodSugarMgDl;
        if (clampedBloodSugar < 22) {
            log.warn("Blood sugar (mg/dL) {} below model min, clamping to 22", bloodSugarMgDl);
            clampedBloodSugar = 22;
        } else if (clampedBloodSugar > 490) {
            log.warn("Blood sugar (mg/dL) {} above model max, clamping to 490", bloodSugarMgDl);
            clampedBloodSugar = 490;
        }

        // hypertensionScore đã là giá trị liên tục 0.0-1.0, clamp an toàn
        double clampedHtn = Math.max(0.0, Math.min(1.0, hypertensionScore));
        double[] x = {clampedAge, clampedDiastolic, clampedBloodSugar, clampedHtn};
        double z = intercept;
        for (int i = 0; i < coefficients.length; i++) {
            z += coefficients[i] * (x[i] - scalerMean[i]) / scalerScale[i];
        }
        return (1.0 / (1.0 + Math.exp(-z))) * 100;
    }

    public RiskLevel mapToRiskLevel(double pct) {
        if (pct < 25) return RiskLevel.LOW;
        if (pct < 50) return RiskLevel.MEDIUM;
        if (pct < 75) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }
}