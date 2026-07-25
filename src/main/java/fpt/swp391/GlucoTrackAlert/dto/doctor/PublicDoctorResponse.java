package fpt.swp391.GlucoTrackAlert.dto.doctor;

import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
import fpt.swp391.GlucoTrackAlert.model.doctor.Doctor;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO công khai (public-facing) dùng cho các API mà PATIENT hoặc VISITOR gọi
 * để xem danh sách / thông tin bác sĩ (VD: /api/patient/assignments/doctors,
 * trang "Chọn bác sĩ đồng hành", "Featured Doctors"...).
 *
 * KHÔNG được thêm các field nhạy cảm sau vào đây:
 *  - nationalId / nationalIdImageUrl        (CCCD)
 *  - practiceLicense / practiceLicenseImageUrl (chứng chỉ hành nghề)
 *  - pendingVerificationJson                 (hồ sơ verify đang chờ duyệt)
 *  - phone                                   (SĐT cá nhân — chỉ lộ sau khi đã được assign chính thức)
 *
 * Những field trên chỉ nên nằm trong DoctorResponse (dùng cho ADMIN / chính DOCTOR đó).
 */
@Getter
@Setter
public class PublicDoctorResponse {

    private Long id;
    private String fullName;
    private String specialization;
    private String degree;
    private Integer experienceYears;
    private String workplace;
    private String introduction;
    private String avatarUrl;
    private String status;

    // Giờ làm việc cố định – mọi bác sĩ đều giống nhau, không lưu DB
    private final String workingHours = WorkShift.DISPLAY;
    private final String workingDays = WorkShift.DAYS;
    private final String workingFullLabel = WorkShift.FULL_LABEL;

    public static PublicDoctorResponse from(Doctor d) {
        PublicDoctorResponse r = new PublicDoctorResponse();
        r.setId(d.getId());
        r.setFullName(d.getFullName());
        r.setSpecialization(d.getSpecialization());
        r.setDegree(d.getDegree());
        r.setExperienceYears(d.getExperienceYears());
        r.setWorkplace(d.getWorkplace());
        r.setIntroduction(d.getIntroduction());
        r.setAvatarUrl(d.getAvatarUrl());
        r.setStatus(d.getStatus());
        return r;
    }
}