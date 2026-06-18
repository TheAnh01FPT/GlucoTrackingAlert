package fpt.swp391.GlucoTrackAlert.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO nhận data từ form Admin khi thêm / sửa bác sĩ. userId bắt buộc khi tạo
 * mới (phải chọn user đã có trong hệ thống).
 */
@Getter
@Setter
public class DoctorRequest {

    private String fullName;
    private String specialization;
    private String degree;
    private Integer experienceYears;
    private String workplace;
    private String phone;
    private String introduction;
    private String avatarUrl;
    private String status;
    private String nationalId;
    private String practiceLicense;
}