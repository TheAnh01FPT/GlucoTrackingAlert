package fpt.swp391.GlucoTrackAlert.dto.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactReplyRequest {

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    @Size(max = 10000, message = "Nội dung phản hồi tối đa 10000 ký tự")
    private String replyContent;
}
