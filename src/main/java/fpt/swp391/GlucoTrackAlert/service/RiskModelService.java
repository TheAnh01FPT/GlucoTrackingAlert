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
            JsonNode root = mapper.readTree(new ClassPathResource("ai-model/ckd-risk-model.json").getInputStream());
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

    public double predictRiskPercentage(double age, double diastolic, double bloodSugar, boolean hypertension) {
        double[] x = {age, diastolic, bloodSugar, hypertension ? 1.0 : 0.0};
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