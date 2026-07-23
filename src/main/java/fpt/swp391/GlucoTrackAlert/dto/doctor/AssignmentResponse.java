package fpt.swp391.GlucoTrackAlert.dto.doctor;

import fpt.swp391.GlucoTrackAlert.model.doctor.DoctorPatientAssignment;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentResponse {

    // FIX 4: Đổi Integer → Long nhất quán
    private Long id;

    // Doctor info
    private Long doctorId;
    private String doctorName;
    private Long patientId;
    private String patientName;

    private LocalDateTime assignedAt;
    private String status;
    private String note;
    private String rejectReason;
    private String cancelReason;

    public static AssignmentResponse from(DoctorPatientAssignment a) {
        AssignmentResponse r = new AssignmentResponse();
        r.setId(a.getId());
        if (a.getDoctor() != null) {
            r.setDoctorId(a.getDoctor().getId());
            r.setDoctorName(a.getDoctor().getFullName());
        }
        if (a.getPatient() != null) {
            r.setPatientId(a.getPatient().getId());
            r.setPatientName(a.getPatient().getFullName());
        }
        r.setAssignedAt(a.getAssignedAt());
        r.setStatus(a.getStatus());
        r.setNote(a.getNote());
        r.setRejectReason(a.getRejectReason());
        r.setCancelReason(a.getCancelReason());
        return r;
    }
}