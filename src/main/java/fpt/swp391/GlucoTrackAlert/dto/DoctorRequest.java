package fpt.swp391.GlucoTrackAlert.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO nhận data từ form Admin khi thêm / sửa bác sĩ.
 */
@Getter
@Setter
public class DoctorRequest {

    @Size(min = 2, max = 100, message = "Họ tên phải từ 2-100 ký tự")
    private String fullName;

    @Size(max = 100, message = "Chuyên khoa không được vượt quá 100 ký tự")
    private String specialization;

    @Size(max = 100, message = "Bằng cấp không được vượt quá 100 ký tự")
    private String degree;

    @Min(value = 0, message = "Số năm kinh nghiệm không được âm")
    @Max(value = 60, message = "Số năm kinh nghiệm không hợp lệ")
    private Integer experienceYears;

    @Size(max = 200, message = "Nơi công tác không được vượt quá 200 ký tự")
    private String workplace;

    @Pattern(regexp = "^$|^(0[3|5|7|8|9])\\d{8}$", message = "Số điện thoại không hợp lệ (phải là số VN 10 chữ số, bắt đầu bằng 03/05/07/08/09)")
    private String phone;

    @Size(max = 1000, message = "Giới thiệu không được vượt quá 1000 ký tự")
    private String introduction;

    private String avatarUrl;

    @Pattern(regexp = "^(active|inactive|pending_verification|pending_approval|rejected)$",
            message = "Status không hợp lệ")
    private String status;

    /**
     * Số căn cước công dân
     */
    @Pattern(regexp = "^$|^\\d{12}$", message = "Số CCCD phải gồm đúng 12 chữ số")
    private String nationalId;

    /**
     * Số chứng chỉ hành nghề
     */
    @Size(max = 50, message = "Số chứng chỉ hành nghề không được vượt quá 50 ký tự")
    private String practiceLicense;
}
