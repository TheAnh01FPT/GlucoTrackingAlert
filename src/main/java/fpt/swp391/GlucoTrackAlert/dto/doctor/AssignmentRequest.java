package fpt.swp391.GlucoTrackAlert.dto.doctor;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO nhận data từ client khi tạo / cập nhật phân công bác sĩ - bệnh nhân.
 * Không nhận trực tiếp entity DoctorPatientAssignment để tránh mass-assignment
 * (client gửi tùy ý id, assignedAt, ...).
 */
@Getter
@Setter
public class AssignmentRequest {

    private Long doctorId;
    private Long patientId;
    private String note;
    private String status;
}
