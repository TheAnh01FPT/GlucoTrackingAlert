package fpt.swp391.GlucoTrackAlert.dto.medication;

import lombok.*;

/**
 * DTO trả về cho dropdown chọn thuốc ở trang kê đơn (prescriptions.html).
 * Các field default* dùng để tự động điền dosage/frequency/... khi bác sĩ chọn thuốc,
 * bác sĩ vẫn sửa được trước khi submit.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MedicineResponse {
    private Long id;
    private String name;
    private String defaultDosage;
    private String defaultFrequency;
    private String defaultTimeOfDay;
    private Integer defaultDurationDays;
    private String defaultInstructions;
    private String contraindications;
}