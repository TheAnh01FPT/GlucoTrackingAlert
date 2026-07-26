package fpt.swp391.GlucoTrackAlert.dto.doctor;

import jakarta.validation.constraints.*;
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
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
    private String email;

    /**
     * Mật khẩu tạm thời do admin tạo.
     * Nếu để trống, hệ thống tự sinh mật khẩu ngẫu nhiên 10 ký tự.
     */
    @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6-100 ký tự")
    private String temporaryPassword;

    // ── Thông tin hồ sơ bác sĩ ───────────────────────────────
    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2-100 ký tự")
    @Pattern(regexp = "^\\p{L}+(?:[ .'-]\\p{L}+)*$",
            message = "Họ tên không hợp lệ: chỉ chữ cái, không khoảng trắng đầu/cuối hoặc liên tiếp, không số/ký tự đặc biệt")
    private String fullName;

    @NotBlank(message = "Chuyên khoa không được để trống")
    @Size(min = 2, max = 100, message = "Chuyên khoa phải từ 2-100 ký tự")
    @Pattern(regexp = "^\\p{L}+(?:[ .'-]\\p{L}+)*$",
            message = "Chuyên khoa không hợp lệ: chỉ chữ cái, không khoảng trắng đầu/cuối hoặc liên tiếp, không số/ký tự đặc biệt")
    private String specialization;

    @Pattern(regexp = "^$|^(BS|BSCKII|ThS|TS|PGS\\.TS|GS\\.TS)$", message = "Bằng cấp không hợp lệ")
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

    // Không có nationalId / practiceLicense ở đây: CCCD và chứng chỉ hành nghề
    // do chính bác sĩ tự nhập + upload ảnh sau khi đăng nhập lần đầu
    // (xem DoctorServiceImp#uploadVerificationImages), admin không nhập hộ.
}