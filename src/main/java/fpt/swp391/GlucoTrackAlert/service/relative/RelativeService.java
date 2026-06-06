package fpt.swp391.GlucoTrackAlert.service.relative;

import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeRequest;
import fpt.swp391.GlucoTrackAlert.dto.relative.RelativeResponse;
import java.util.List;

public interface RelativeService {
    List<RelativeResponse> getRelativesByPatientId(Long patientId);
    RelativeResponse getRelativeById(Long relativeId);
    RelativeResponse addRelative(RelativeRequest request);
    RelativeResponse updateRelative(Long relativeId, RelativeRequest request);
    void deleteRelative(Long relativeId);
    RelativeResponse toggleNotification(Long relativeId, boolean enabled);
}
