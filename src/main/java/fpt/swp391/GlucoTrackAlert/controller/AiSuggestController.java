package fpt.swp391.GlucoTrackAlert.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Proxy Claude API phía backend — giữ API key an toàn, không lộ ra frontend.
 * Frontend gọi POST /api/ai/suggest với thông tin bệnh nhân,
 * controller đọc dataset CSV rồi trả JSON đơn thuốc phù hợp về.
 *
 * Nếu cấu hình API key Claude, sẽ gọi Claude API thay vì dùng dataset.
 * Dataset: src/main/resources/diabetes_medication_dataset.csv
 */
@RestController
@RequestMapping("/api/ai")
public class AiSuggestController {

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Dataset loaded at startup
    private final List<Map<String, String>> dataset = new ArrayList<>();

    @PostConstruct
    public void loadDataset() {
        try {
            ClassPathResource resource = new ClassPathResource("diabetes_medication_dataset.csv");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            String headerLine = reader.readLine();
            if (headerLine == null) return;
            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                // Handle quoted fields with commas inside
                String[] values = parseCSVLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim().replace("\"", ""));
                }
                dataset.add(row);
            }
            reader.close();
            System.out.println("[AiSuggestController] Loaded " + dataset.size() + " records from dataset.");
        } catch (Exception e) {
            System.err.println("[AiSuggestController] Failed to load dataset: " + e.getMessage());
        }
    }

    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) { fields.add(sb.toString()); sb.setLength(0); }
            else { sb.append(c); }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    @PostMapping("/suggest")
    public ResponseEntity<?> suggest(@RequestBody Map<String, Object> patientData) {
        if (anthropicApiKey != null && !anthropicApiKey.isBlank()) {
            return callClaudeApi(patientData);
        }
        return ResponseEntity.ok(buildDatasetSuggestion(patientData));
    }

    private ResponseEntity<?> callClaudeApi(Map<String, Object> patientData) {
        try {
            String prompt = buildPrompt(patientData);
            ObjectNode body = mapper.createObjectNode();
            body.put("model", "claude-sonnet-4-6");
            body.put("max_tokens", 1000);
            ArrayNode messages = mapper.createArrayNode();
            ObjectNode msg = mapper.createObjectNode();
            msg.put("role", "user");
            msg.put("content", prompt);
            messages.add(msg);
            body.set("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", anthropicApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ResponseEntity.ok(buildDatasetSuggestion(patientData));
            }
            JsonNode root = mapper.readTree(response.body());
            String text = root.path("content").get(0).path("text").asText();
            String jsonStr = text.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
            JsonNode suggestion = mapper.readTree(jsonStr);
            ((ObjectNode) suggestion).put("mode", "ai");
            return ResponseEntity.ok(suggestion);
        } catch (Exception e) {
            return ResponseEntity.ok(buildDatasetSuggestion(patientData));
        }
    }

    /**
     * Gợi ý đơn thuốc dựa trên dataset CSV.
     * Đọc từng row trong dataset, so khớp điều kiện bệnh nhân,
     * gom các thuốc phù hợp lại thành đơn thuốc.
     */
    private ObjectNode buildDatasetSuggestion(Map<String, Object> d) {
        double bloodSugar = parseDouble(d.get("bloodSugar"), 10.0);
        double bmi        = parseDouble(d.get("patientBmi"), 22.0);
        int    age        = parseInt(d.get("patientAge"), 40);
        String gender     = String.valueOf(d.getOrDefault("patientGender", "")).toUpperCase();

        String matchedCase = resolveCase(bloodSugar, bmi, age, gender);

        ObjectNode result = mapper.createObjectNode();
        result.put("mode", "dataset");
        ArrayNode items = mapper.createArrayNode();
        String note = "";

        for (Map<String, String> row : dataset) {
            if (!row.get("condition").equals(matchedCase)) continue;

            // Validate age range
            int minAge = parseInt2(row.get("min_age"), 0);
            int maxAge = parseInt2(row.get("max_age"), 99);
            if (age < minAge || age > maxAge) continue;

            // Validate gender
            String rowGender = row.get("gender").toUpperCase();
            if (!rowGender.equals("ALL")) {
                boolean isFemale = gender.contains("F") || gender.contains("NỮ")
                        || gender.contains("NU") || gender.equals("FEMALE") || gender.equals("NAM");
                if (rowGender.equals("FEMALE") && !isFemale) continue;
            }

            // Validate blood sugar range
            double minBs = parseDouble2(row.get("min_blood_sugar"), 0);
            double maxBs = parseDouble2(row.get("max_blood_sugar"), 99);
            if (bloodSugar < minBs || bloodSugar > maxBs) continue;

            // Validate BMI range
            double minBmi = parseDouble2(row.get("min_bmi"), 0);
            double maxBmi = parseDouble2(row.get("max_bmi"), 99);
            if (bmi < minBmi || bmi > maxBmi) continue;

            note = row.get("note");
            ObjectNode item = mapper.createObjectNode();
            item.put("medicineName",  row.get("medicine_name"));
            item.put("dosage",        row.get("dosage"));
            item.put("frequency",     row.get("frequency"));
            item.put("timeOfDay",     row.get("time_of_day"));
            item.put("durationDays",  parseInt2(row.get("duration_days"), 30));
            item.put("instructions",  row.get("instructions"));
            items.add(item);
        }

        result.put("note", note.isEmpty() ? "Gợi ý từ dataset phác đồ ADA 2023" : note);
        result.set("items", items);
        return result;
    }

    /**
     * Xác định case dựa trên thông tin bệnh nhân — logic giống buildMockSuggestion cũ
     * nhưng thay vì hardcode thuốc, chỉ trả về tên case để lookup trong dataset.
     */
    private String resolveCase(double bloodSugar, double bmi, int age, String gender) {
        boolean isFemale = gender.contains("F") || gender.contains("NỮ")
                || gender.contains("NU") || gender.equals("FEMALE");
        if (isFemale && age >= 18 && age <= 45 && bloodSugar >= 9.0) return "pregnant_risk";
        if (age >= 70 && bloodSugar >= 10.0) return "elderly_high";
        if (age >= 70) return "elderly";
        if (age < 18) return "pediatric";
        if (bmi >= 30.0 && bloodSugar >= 10.0) return "obese_high";
        if (bmi >= 30.0) return "obese";
        if (bloodSugar >= 14.0) return "very_high";
        if (bloodSugar >= 11.0) return "high";
        return "mild";
    }

    private String buildPrompt(Map<String, Object> d) {
        return "Bạn là bác sĩ nội tiết. Dựa vào dữ liệu sau, hãy gợi ý đơn thuốc điều trị tiểu đường type 2 theo phác đồ ADA 2023.\n\n"
                + "Thông tin bệnh nhân:\n"
                + "- Tuổi: "           + d.getOrDefault("patientAge",    "không rõ") + "\n"
                + "- Giới tính: "      + d.getOrDefault("patientGender", "không rõ") + "\n"
                + "- BMI: "            + d.getOrDefault("patientBmi",    "không rõ") + "\n"
                + "- Đường huyết gần nhất: " + d.getOrDefault("bloodSugar", "không rõ")
                + " mmol/L (đo ngày " + d.getOrDefault("logDate", "không rõ") + ")\n"
                + "- Huyết áp: "       + d.getOrDefault("systolic", "?") + "/" + d.getOrDefault("diastolic", "?") + " mmHg\n"
                + "- Triệu chứng: "    + d.getOrDefault("symptoms", "không có") + "\n\n"
                + "Trả về JSON với cấu trúc sau (chỉ JSON, không giải thích thêm):\n"
                + "{\n"
                + "  \"note\": \"lý do kê đơn ngắn gọn 1 dòng\",\n"
                + "  \"items\": [\n"
                + "    {\n"
                + "      \"medicineName\": \"Metformin 500mg\",\n"
                + "      \"dosage\": \"1 viên / lần\",\n"
                + "      \"frequency\": \"2 lần/ngày\",\n"
                + "      \"timeOfDay\": \"07:00,19:00\",\n"
                + "      \"durationDays\": 30,\n"
                + "      \"instructions\": \"Uống sau ăn\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "Chỉ gợi ý tối đa 3 loại thuốc. timeOfDay dùng định dạng HH:mm, phân cách bằng dấu phẩy.";
    }

    private double parseDouble(Object value, double def) {
        if (value == null) return def;
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return def; }
    }
    private double parseDouble2(String value, double def) {
        if (value == null || value.isBlank()) return def;
        try { return Double.parseDouble(value); } catch (Exception e) { return def; }
    }
    private int parseInt(Object value, int def) {
        if (value == null) return def;
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return def; }
    }
    private int parseInt2(String value, int def) {
        if (value == null || value.isBlank()) return def;
        try { return Integer.parseInt(value); } catch (Exception e) { return def; }
    }
}