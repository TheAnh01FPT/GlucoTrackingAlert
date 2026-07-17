package fpt.swp391.GlucoTrackAlert.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO dùng chung khi Admin từ chối đề xuất, hoặc khi cần truyền lý do hủy
 * một phân công đang active (do bệnh nhân đổi sang bác sĩ khác).
 */
@Getter
@Setter
public class ReasonRequest {

    private String reason;
}