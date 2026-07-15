package fpt.swp391.GlucoTrackAlert.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO bệnh nhân gửi khi đề xuất bác sĩ đồng hành.
 * Patient lấy từ user đang đăng nhập, không nhận từ client để tránh giả mạo.
 */
@Getter
@Setter
public class ProposeAssignmentRequest {

    private Long doctorId;
    private String note;
}