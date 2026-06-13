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
    DoctorResponse updateDoctor(Integer id, DoctorRequest request);

    DoctorResponse getDoctorById(Integer id);

    List<DoctorResponse> getAllDoctors();

    /**
     * Soft-delete: set status = inactive, hủy hết assignment active.
     */
    void deactivateDoctor(Integer id);

    /**
     * Bác sĩ upload ảnh CCCD và chứng chỉ hành nghề.
     * Status chuyển sang pending_approval để admin duyệt.
     */
    DoctorResponse uploadVerificationImages(Integer doctorId,
            String nationalIdImageUrl, String practiceLicenseImageUrl);

    /**
     * Admin lấy danh sách bác sĩ đang chờ duyệt (status = pending_approval).
     */
    List<DoctorResponse> getPendingDoctors();

    /**
     * Admin duyệt bác sĩ → status = active.
     */
    DoctorResponse approveDoctor(Integer id);

    /**
     * Admin từ chối bác sĩ → status = rejected.
     */
    DoctorResponse rejectDoctor(Integer id, String reason);

}