package fpt.swp391.GlucoTrackAlert.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Admin dùng DTO này để tạo tài khoản bác sĩ từ đầu:
 *   1. Tạo User (email + password do admin đặt)
 *   2. Tạo Doctor profile liên kết với User đó
 *   3. Gửi email thông báo cho bác sĩ (kèm username + mật khẩu tạm thời)
 *
 * Bác sĩ KHÔNG tự đăng ký được. Admin tạo hộ và gửi thông tin qua email.
 */
@Getter
@Setter
public class AdminCreateDoctorRequest {

    // ── Thông tin tài khoản (admin đặt) ──────────────────────
    /** Email đăng nhập của bác sĩ */
    private String email;

    /**
     * Mật khẩu tạm thời do admin tạo.
     * Nếu để trống, hệ thống tự sinh mật khẩu ngẫu nhiên 10 ký tự.
     */
    private String temporaryPassword;

    // ── Thông tin hồ sơ bác sĩ ───────────────────────────────
    private String fullName;
    private String specialization;
    private String degree;
    private Integer experienceYears;
    private String workplace;
    private String phone;
    private String introduction;
    private String avatarUrl;

    /** Số căn cước công dân */
    private String nationalId;

    /** Số chứng chỉ hành nghề */
    private String practiceLicense;
}