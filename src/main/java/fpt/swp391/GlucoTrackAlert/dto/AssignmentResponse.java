package fpt.swp391.GlucoTrackAlert.dto;

import fpt.swp391.GlucoTrackAlert.model.DoctorPatientAssignment;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentResponse {

    // FIX 4: Đổi Integer → Long nhất quán
    private Long id;
    private Long doctorId;
    private String doctorName;
    private Long patientId;
    private String patientName;

    private LocalDateTime assignedAt;
    private String status;
    private String note;

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
        return r;
    }
}