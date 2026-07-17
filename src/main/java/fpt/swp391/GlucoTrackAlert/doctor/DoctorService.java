package fpt.swp391.GlucoTrackAlert.doctor;

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

    /**
     * Bác sĩ upload ảnh CCCD, chứng chỉ hành nghề, avatar + nhập số CCCD & số
     * chứng chỉ. Status chuyển sang pending_approval để admin duyệt.
     */
    DoctorResponse uploadVerificationImages(Long doctorId,
            String nationalIdImageUrl,
            String practiceLicenseImageUrl,
            String avatarUrl,
            String nationalId,
            String practiceLicense);

    List<DoctorResponse> getPendingDoctors();

    DoctorResponse approveDoctor(Long id);

    DoctorResponse rejectDoctor(Long id, String reason);
}