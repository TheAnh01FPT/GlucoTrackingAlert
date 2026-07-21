package fpt.swp391.GlucoTrackAlert.service.article;

import fpt.swp391.GlucoTrackAlert.dto.article.HealthArticleRequest;
import fpt.swp391.GlucoTrackAlert.model.article.ArticleStatus;
import fpt.swp391.GlucoTrackAlert.model.article.HealthArticle;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Service interface xử lý logic nghiệp vụ bài viết kiến thức y khoa
 */
public interface HealthArticleService {

    /**
     * Lấy danh sách bài viết công khai với tìm kiếm và lọc danh mục
     */
    Page<HealthArticle> getPublishedArticles(String keyword, String category, Pageable pageable);

    /**
     * Lấy chi tiết bài viết theo slug (chỉ nếu PUBLISHED)
     */
    Optional<HealthArticle> getArticleBySlug(String slug);

    /**
     * Lấy danh sách bài viết cho trang quản lý (tất cả trạng thái, cho Bác sĩ/Admin)
     */
    Page<HealthArticle> getArticlesForManagement(String status, String keyword, Pageable pageable, User currentUser, boolean isAdmin);

    /**
     * Lấy chi tiết bài viết theo ID (cho Bác sĩ/Admin quản lý)
     */
    Optional<HealthArticle> getArticleById(Long id);

    /**
     * Tạo bài viết mới
     * Tự động sinh slug từ title, validate trạng thái, đặt publishedAt nếu publish
     */
    HealthArticle createArticle(HealthArticleRequest request, Long createdByUserId) throws Exception;

    /**
     * Cập nhật bài viết
     * Regenerate slug chỉ nếu tiêu đề thay đổi
     */
    HealthArticle updateArticle(Long id, HealthArticleRequest request) throws Exception;

    /**
     * Cập nhật trạng thái bài viết cho duyệt/từ chối
     */
    HealthArticle updateArticleStatus(Long id, ArticleStatus status, String rejectionReason) throws Exception;

    /**
     * Xóa bài viết
     */
    void deleteArticle(Long id) throws Exception;

    /**
     * Sinh slug duy nhất từ tiêu đề
     * - Bỏ dấu tiếng Việt, chuyển thành chữ thường
     * - Khoảng trắng → `-`
     * - Nếu trùng → thêm hậu tố `-2`, `-3`, ...
     */
    String generateSlug(String title) throws Exception;
}
