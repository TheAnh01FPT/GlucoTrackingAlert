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

    @NotBlank(message = "Email không được bỏ trống")
    @Email(message = "Địa chỉ email không đúng định dạng")
    @Size(max = 150, message = "Email không vượt quá 150 ký tự")
    private String email;

    // Khi tạo mới bắt buộc nhập, khi cập nhật nếu để trống nghĩa là không thay đổi mật khẩu cũ
    private String password;

    @NotBlank(message = "Trạng thái tài khoản không được bỏ trống")
    private String status; // active, pending_verification, banned

    private Boolean emailVerified;

    @NotBlank(message = "Vui lòng chỉ định quyền vai trò hệ thống")
    private String roleName; // ADMIN, PATIENT, DOCTOR, RELATIVE,...
}