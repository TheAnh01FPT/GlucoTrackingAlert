package fpt.swp391.GlucoTrackAlert.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Pattern;
/**
 * DTO nhận dữ liệu từ form tạo/sửa bài viết
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HealthArticleRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(min = 5, max = 255, message = "Tiêu đề phải từ 5-255 ký tự")
    private String title;

    @Size(max = 500, message = "Tóm tắt không được vượt quá 500 ký tự")
    private String summary;

    @NotBlank(message = "Nội dung không được để trống")
    @Size(min = 10, message = "Nội dung phải có ít nhất 10 ký tự")
    @Size(max = 50000, message = "Nội dung không được vượt quá 50000 ký tự")
    private String content;

    // Tệp ảnh upload mới (multipart). Khi edit, có thể null/empty để giữ thumbnailUrl cũ
    private MultipartFile thumbnailFile;

    // URL ảnh hiện tại (giữ nguyên nếu edit mà không upload file mới)
    private String thumbnailUrl;

    @Size(max = 100, message = "Danh mục không được vượt quá 100 ký tự")
    private String category;

    @NotBlank(message = "Trạng thái không được để trống")
    @Pattern(regexp = "^(draft|published)$", message = "Trạng thái phải là draft hoặc published")
    private String status;
}
