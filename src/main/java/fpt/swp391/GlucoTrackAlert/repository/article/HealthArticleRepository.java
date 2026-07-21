package fpt.swp391.GlucoTrackAlert.repository.article;

import fpt.swp391.GlucoTrackAlert.model.article.ArticleStatus;
import fpt.swp391.GlucoTrackAlert.model.article.HealthArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository xử lý các câu query liên quan đến bài viết kiến thức y khoa
 */
@Repository
public interface HealthArticleRepository extends JpaRepository<HealthArticle, Long> {

    /**
     * Tìm bài viết theo slug
     */
    Optional<HealthArticle> findBySlug(String slug);

    /**
     * Tìm bài viết theo slug và trạng thái (để đảm bảo chỉ lấy bài published công khai)
     */
    Optional<HealthArticle> findBySlugAndStatus(String slug, ArticleStatus status);

    /**
     * Kiểm tra slug đã tồn tại chưa
     */
    boolean existsBySlug(String slug);

    /**
     * Lấy danh sách bài viết công khai (chỉ PUBLISHED) với tìm kiếm và lọc danh mục, có phân trang
     * Tìm kiếm trong title và summary
     */
    @Query("SELECT a FROM HealthArticle a WHERE a.status = :status " +
           "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(a.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:category IS NULL OR :category = '' OR a.category = :category) " +
           "ORDER BY a.publishedAt DESC")
    Page<HealthArticle> findPublishedArticles(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("status") ArticleStatus status,
            Pageable pageable);

    /**
     * Lấy danh sách bài viết cho trang quản lý (lọc theo status, keyword, sort theo updatedAt DESC)
     * Dùng cho Bác sĩ/Admin xem toàn bộ bài (draft, published)
     */
    @Query("SELECT a FROM HealthArticle a WHERE " +
           "(:status IS NULL OR a.status = :status) " +
           "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(a.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (a.status = 'PUBLISHED' OR a.status = 'REJECTED' OR a.status = 'PENDING_REVIEW') " +
           "ORDER BY a.updatedAt DESC")
    Page<HealthArticle> findForManagementForAdmin(
            @Param("status") ArticleStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT a FROM HealthArticle a WHERE " +
           "(:status IS NULL OR a.status = :status) " +
           "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(a.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND a.createdBy.id = :userId " +
           "ORDER BY a.updatedAt DESC")
    Page<HealthArticle> findForManagementForDoctor(
            @Param("status") ArticleStatus status,
            @Param("keyword") String keyword,
            @Param("userId") Long userId,
            Pageable pageable);
}
