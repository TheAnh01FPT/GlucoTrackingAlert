package fpt.swp391.GlucoTrackAlert.service;

import fpt.swp391.GlucoTrackAlert.dto.AdminCreateDoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorRequest;
import fpt.swp391.GlucoTrackAlert.dto.DoctorResponse;
import java.util.List;

public interface DoctorService {

    DoctorResponse adminCreateDoctor(AdminCreateDoctorRequest request) throws Exception;

    DoctorResponse updateDoctor(Long id, DoctorRequest request);

    DoctorResponse getDoctorById(Long id);

    List<DoctorResponse> getAllDoctors();

    void deactivateDoctor(Long id);

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
