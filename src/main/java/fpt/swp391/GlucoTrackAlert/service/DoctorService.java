package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.dto.AdminCreateDoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorResponse;
import java.util.List;

public interface DoctorService {

    /**
     * Admin tạo tài khoản User + Doctor profile cùng lúc, rồi gửi email chứa
     * username & mật khẩu tạm cho bác sĩ.
     */
    DoctorResponse adminCreateDoctor(AdminCreateDoctorRequest request) throws Exception;

    /**
     * Admin cập nhật thông tin hồ sơ bác sĩ (bao gồm cả phone).
     */
    DoctorResponse updateDoctor(Long id, DoctorRequest request);

    DoctorResponse getDoctorById(Long id);

    List<DoctorResponse> getAllDoctors();

    /**
     * Soft-delete: set status = inactive, hủy hết assignment active.
     */
    void deactivateDoctor(Long id);

    /**
     * Hard-delete: xóa vĩnh viễn Doctor + User liên kết khỏi DB. Chỉ thực hiện
     * được khi bác sĩ đang ở trạng thái inactive.
     */
    void hardDeleteDoctor(Long id);
}