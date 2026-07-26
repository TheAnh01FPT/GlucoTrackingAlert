package fpt.swp391.GlucoTrackAlert.model.medication;

import jakarta.persistence.*;
import lombok.*;

/**
 * Danh mục thuốc cố định để bác sĩ chọn qua dropdown khi kê đơn
 * (thay cho tính năng "Gợi ý AI" đã bị loại bỏ).
 * Các field "default*" chỉ dùng để tự động điền vào form kê đơn,
 * bác sĩ vẫn có thể sửa tay trước khi lưu PrescriptionItem.
 */
@Entity
@Table(name = "medicines")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(name = "default_dosage", length = 100)
    private String defaultDosage;

    @Column(name = "default_frequency", length = 50)
    private String defaultFrequency;

    // "07:00,19:00" — phân cách bằng dấu phẩy
    @Column(name = "default_time_of_day", length = 100)
    private String defaultTimeOfDay;

    @Column(name = "default_duration_days")
    private Integer defaultDurationDays;

    @Column(name = "default_instructions", columnDefinition = "TEXT")
    private String defaultInstructions;

    @Column(columnDefinition = "TEXT")
    private String contraindications;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}