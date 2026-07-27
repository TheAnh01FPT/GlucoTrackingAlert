package fpt.swp391.GlucoTrackAlert.service.patient;

import fpt.swp391.GlucoTrackAlert.model.patient.ProfileChangeRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import java.util.List;

public interface ProfileChangeRequestService {
    ProfileChangeRequest createRequest(Long userId, String fieldName, String reason, MultipartFile evidenceFile);
    List<ProfileChangeRequest> getRequestsByPatient(Long patientId);
    Page<ProfileChangeRequest> getRequestsByPatientPaged(Long patientId, int page, int size);
    List<ProfileChangeRequest> getAllRequests();
    Page<ProfileChangeRequest> getAllRequestsPaged(int page, int size);
    ProfileChangeRequest approveRequest(Long requestId, Long adminUserId);
    ProfileChangeRequest rejectRequest(Long requestId, Long adminUserId, String rejectionReason);
    boolean hasPendingRequest(Long patientId, String fieldName);
}
