package fpt.swp391.GlucoTrackAlert.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO cho bác sĩ tự cập nhật thông tin cá nhân.
 * Chỉ cho phép sửa các trường không cần xác minh.
 * Admin-only fields (status, specialization, degree) không có ở đây.
 */
@Getter
@Setter
public class DoctorSelfUpdateRequest {
    private String phone;
    private String workplace;
    private String introduction;
}