package fpt.swp391.GlucoTrackAlert.dto;

import fpt.swp391.GlucoTrackAlert.enums.WorkShift;
import fpt.swp391.GlucoTrackAlert.model.Doctor;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO trả về cho frontend – không expose toàn bộ User object. Giờ làm việc là
 * cố định toàn hệ thống, lấy từ WorkShift constants.
 */
@Getter
@Setter
public class DoctorResponse {

    // FIX 4: Đổi Integer → Long nhất quán với Doctor.id
    private Long id;
    private Long userId;
    private String userEmail;
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
    private String nationalIdImageUrl;
    private String practiceLicenseImageUrl;

    // Giờ làm việc cố định – mọi bác sĩ đều giống nhau, không lưu DB
    private final String workingHours = WorkShift.DISPLAY;
    private final String workingDays = WorkShift.DAYS;
    private final String workingFullLabel = WorkShift.FULL_LABEL;

    public static DoctorResponse from(Doctor d) {
        DoctorResponse r = new DoctorResponse();
        r.setId(d.getId());
        if (d.getUser() != null) {
            r.setUserId(d.getUser().getId());
            r.setUserEmail(d.getUser().getEmail());
        }
        r.setFullName(d.getFullName());
        r.setSpecialization(d.getSpecialization());
        r.setDegree(d.getDegree());
        r.setExperienceYears(d.getExperienceYears());
        r.setWorkplace(d.getWorkplace());
        r.setPhone(d.getPhone());
        r.setIntroduction(d.getIntroduction());
        r.setAvatarUrl(d.getAvatarUrl());
        r.setStatus(d.getStatus());
        r.setNationalId(d.getNationalId());
        r.setPracticeLicense(d.getPracticeLicense());
        r.setNationalIdImageUrl(d.getNationalIdImageUrl());
        r.setPracticeLicenseImageUrl(d.getPracticeLicenseImageUrl());
        return r;
    }
}