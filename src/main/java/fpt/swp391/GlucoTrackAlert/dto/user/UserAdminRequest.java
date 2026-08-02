package fpt.swp391.GlucoTrackAlert.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAdminRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng Email không hợp lệ")
    @Size(max = 150, message = "Email không vượt quá 150 ký tự")
    private String email;

    @Size(max = 100, message = "Họ và tên không vượt quá 100 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại không vượt quá 20 ký tự")
    private String phone;

    // Khi tạo mới bắt buộc nhập, khi cập nhật nếu để trống nghĩa là không thay đổi mật khẩu cũ
    private String password;

    private String status; // active, pending_verification, banned

    private Boolean emailVerified;

    @NotBlank(message = "Vui lòng chỉ định quyền vai trò hệ thống")
    private String roleName; // ADMIN, PATIENT, DOCTOR, RELATIVE,...
}