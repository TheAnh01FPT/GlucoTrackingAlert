package fpt.swp391.GlucoTrackAlert.service.article;

import fpt.swp391.GlucoTrackAlert.dto.article.HealthArticleRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.model.article.ArticleStatus;
import fpt.swp391.GlucoTrackAlert.model.article.HealthArticle;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.repository.article.HealthArticleRepository;
import fpt.swp391.GlucoTrackAlert.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/**
 * Service implementation xử lý logic nghiệp vụ bài viết kiến thức y khoa
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HealthArticleServiceImpl implements HealthArticleService {

    private final HealthArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    private static final PolicyFactory CONTENT_POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "b", "em", "i", "u", "s", "h2", "h3", "h4", "ul", "ol", "li", "blockquote", "a", "img")
            .allowAttributes("href").onElements("a")
            .requireRelNofollowOnLinks()
            .allowAttributes("src", "alt").onElements("img")
            .allowUrlProtocols("https")
            .toFactory();

    @Override
    public Page<HealthArticle> getPublishedArticles(String keyword, String category, Pageable pageable) {
        String searchKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword.trim();
        return articleRepository.findPublishedArticles(searchKeyword, category, ArticleStatus.PUBLISHED, pageable);
    }

    @Override
    public Optional<HealthArticle> getArticleBySlug(String slug) {
        return articleRepository.findBySlugAndStatus(slug, ArticleStatus.PUBLISHED);
    }

    @Override
    public Page<HealthArticle> getArticlesForManagement(String status, String keyword, Pageable pageable, User currentUser, boolean isAdmin) {
        String searchKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword.trim();
        ArticleStatus searchStatus = (status == null || status.isBlank())
                ? null
                : ArticleStatus.fromString(status.trim());
        if (isAdmin) {
            return articleRepository.findForManagementForAdmin(searchStatus, searchKeyword, pageable);
        }
        return articleRepository.findForManagementForDoctor(searchStatus, searchKeyword, currentUser.getId(), pageable);
    }

    @Override
    public Optional<HealthArticle> getArticleById(Long id) {
        return articleRepository.findById(id);
    }

    @Override
    public HealthArticle createArticle(HealthArticleRequest request, Long createdByUserId) throws Exception {
        // Validate
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new Exception("Tiêu đề bài viết không được để trống");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new Exception("Nội dung bài viết không được để trống");
        }

        // Lấy User tạo bài
        User createdBy = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new Exception("User không tồn tại"));

        // Sinh slug duy nhất
        String slug = generateSlug(request.getTitle());

        String safeContent = CONTENT_POLICY.sanitize(request.getContent());

        // Validate trạng thái
        ArticleStatus status = validateAndGetStatus(request.getStatus());

        // Xử lý file thumbnail nếu có
        String savedThumbnailUrl = null;
        MultipartFile thumbFile = request.getThumbnailFile();
        if (thumbFile != null && !thumbFile.isEmpty()) {
            String contentType = thumbFile.getContentType();
            if (contentType == null || 
                    !(contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/gif") || contentType.equals("image/webp"))) {
                throw new Exception("Chỉ chấp nhận file ảnh JPG, PNG, GIF hoặc WEBP");
            }

            if (thumbFile.getSize() > 5L * 1024L * 1024L) {
                throw new Exception("Kích thước ảnh không được vượt quá 5MB");
            }

            try {
                savedThumbnailUrl = cloudinaryService.uploadFile(thumbFile, "health_articles");
            } catch (IOException e) {
                throw new RuntimeException("Lỗi khi upload thumbnail lên Cloudinary: " + e.getMessage(), e);
            }
        }

        // Tạo entity
        HealthArticle article = HealthArticle.builder()
                .title(request.getTitle())
                .slug(slug)
                .summary(request.getSummary())
                .content(safeContent)
                .thumbnailUrl(savedThumbnailUrl)
                .category(request.getCategory())
                .status(status)
                .createdBy(createdBy)
                .build();

        article.setRejectionReason(null);

        // Nếu publish, set publishedAt = now
        if (status == ArticleStatus.PUBLISHED) {
            article.setPublishedAt(LocalDateTime.now());
        }

        return articleRepository.save(article);
    }

    @Override
    public HealthArticle updateArticle(Long id, HealthArticleRequest request) throws Exception {
        // Validate
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new Exception("Tiêu đề bài viết không được để trống");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new Exception("Nội dung bài viết không được để trống");
        }

        HealthArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new Exception("Bài viết không tồn tại"));

        // Regenerate slug chỉ nếu bài chưa được publish lần nào và tiêu đề thay đổi
        if (article.getPublishedAt() == null && !article.getTitle().equals(request.getTitle())) {
            String newSlug = generateSlug(request.getTitle());
            article.setSlug(newSlug);
        }

        article.setTitle(request.getTitle());
        article.setSummary(request.getSummary());
        article.setContent(CONTENT_POLICY.sanitize(request.getContent()));
        // Xử lý file thumbnail nếu có (nếu không có file mới, giữ nguyên URL cũ)
        MultipartFile thumbFile2 = request.getThumbnailFile();
        if (thumbFile2 != null && !thumbFile2.isEmpty()) {
            String contentType = thumbFile2.getContentType();
            if (contentType == null || 
                    !(contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/gif") || contentType.equals("image/webp"))) {
                throw new Exception("Chỉ chấp nhận file ảnh JPG, PNG, GIF hoặc WEBP");
            }

            if (thumbFile2.getSize() > 5L * 1024L * 1024L) {
                throw new Exception("Kích thước ảnh không được vượt quá 5MB");
            }

            try {
                article.setThumbnailUrl(cloudinaryService.uploadFile(thumbFile2, "health_articles"));
            } catch (IOException e) {
                throw new RuntimeException("Lỗi khi upload thumbnail lên Cloudinary: " + e.getMessage(), e);
            }
        }

        article.setCategory(request.getCategory());

        // Validate trạng thái
        ArticleStatus status = validateAndGetStatus(request.getStatus());
        ArticleStatus oldStatus = article.getStatus();
        article.setStatus(status);
        article.setRejectionReason(null);

        // Nếu chuyển từ draft → published, set publishedAt = now
        if (oldStatus != ArticleStatus.PUBLISHED && status == ArticleStatus.PUBLISHED) {
            article.setPublishedAt(LocalDateTime.now());
        }

        return articleRepository.save(article);
    }

    @Override
    public HealthArticle updateArticleStatus(Long id, ArticleStatus status, String rejectionReason) throws Exception {
        HealthArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new Exception("Bài viết không tồn tại"));

        article.setStatus(status);
        if (status == ArticleStatus.PUBLISHED) {
            article.setPublishedAt(LocalDateTime.now());
            article.setRejectionReason(null);
        } else if (status == ArticleStatus.REJECTED) {
            article.setRejectionReason(rejectionReason != null && !rejectionReason.isBlank() ? rejectionReason.trim() : null);
        } else {
            article.setRejectionReason(null);
        }

        return articleRepository.save(article);
    }

    @Override
    public void deleteArticle(Long id) throws Exception {
        HealthArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new Exception("Bài viết không tồn tại"));
        articleRepository.delete(article);
    }

    @Override
    public String generateSlug(String title) throws Exception {
        if (title == null || title.isBlank()) {
            throw new Exception("Tiêu đề không được để trống");
        }

        // Bỏ dấu tiếng Việt (normalize)
        String slug = removeAccents(title);

        // Chuyển thành chữ thường
        slug = slug.toLowerCase();

        // Khoảng trắng, dấu chấm, dấu phẩy → `-`
        slug = slug.replaceAll("[\\s.,'\"!?;:()\\-]+", "-");

        // Loại bỏ `-` ở đầu và cuối
        slug = slug.replaceAll("^-+|-+$", "");

        // Nếu slug rỗng, throw exception
        if (slug.isBlank()) {
            throw new Exception("Không thể tạo slug từ tiêu đề");
        }

        // Xử lý trùng lặp: nếu slug đã tồn tại, thêm hậu tố `-2`, `-3`, ...
        String originalSlug = slug;
        int counter = 2;
        while (articleRepository.existsBySlug(slug)) {
            slug = originalSlug + "-" + counter;
            counter++;
        }

        return slug;
    }

    /**
     * Hàm helper: bỏ dấu tiếng Việt từ chuỗi
     * Sử dụng java.text.Normalizer để decompose unicode
     * Xử lý riêng ký tự đ/Đ
     */
    private String removeAccents(String str) {
        if (str == null || str.isBlank()) {
            return str;
        }

        // Xử lý ký tự đ/Đ trước
        str = str.replace("đ", "d").replace("Đ", "d");

        // Chuẩn hóa Unicode: NFD (decomposed form)
        String normalized = Normalizer.normalize(str, Normalizer.Form.NFD);

        // Loại bỏ các dấu (combining characters)
        // Regex: \\p{M} = combining marks, \\p{Mn} = nonspacing marks
        Pattern pattern = Pattern.compile("\\p{M}");

        return pattern.matcher(normalized).replaceAll("");
    }

    /**
     * Validate trạng thái, mặc định về DRAFT nếu giá trị lạ
     */
    private ArticleStatus validateAndGetStatus(String status) {
        return ArticleStatus.fromString(status);
    }
}
