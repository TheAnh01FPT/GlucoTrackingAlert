package fpt.swp391.GlucoTrackAlert.controller.article;

import fpt.swp391.GlucoTrackAlert.dto.article.HealthArticleRequest;
import fpt.swp391.GlucoTrackAlert.model.article.ArticleStatus;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.model.article.HealthArticle;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.repository.article.HealthArticleRepository;
import fpt.swp391.GlucoTrackAlert.service.article.HealthArticleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Controller quản lý bài viết cho Bác sĩ/Admin
 * [DOCTOR, ADMIN] chỉ những người có role này mới có quyền truy cập
 */
@Controller
@RequestMapping("/articles/manage")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
public class HealthArticleManageController {

    private static final Logger log = LoggerFactory.getLogger(HealthArticleManageController.class);
    private final HealthArticleService articleService;
    private final UserRepository userRepository;
    private final HealthArticleRepository articleRepository;
    private static final int PAGE_SIZE = 5;
    private static final Pattern UNSUPPORTED_EMBED_PATTERN = Pattern.compile("(?i)<\\s*(iframe|video|embed)\\b[^>]*>");

    /**
     * Lấy user đăng nhập hiện tại (tương tự PatientWebController.getLoggedInUser())
     */
    private User getLoggedInUser() throws Exception {
        String email = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new Exception("User không tồn tại"));
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    private void checkOwnership(HealthArticle article, User currentUser) throws AccessDeniedException {
        boolean isAdmin = hasRole("ROLE_ADMIN");
        if (!isAdmin && (article.getCreatedBy() == null || !article.getCreatedBy().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên bài viết này");
        }
    }

    private boolean containsUnsupportedEmbeddedContent(String content) {
        return content != null && UNSUPPORTED_EMBED_PATTERN.matcher(content).find();
    }

    private void populateArticleFormModel(Model model, HealthArticleRequest request, Long id, boolean isEdit) {
        model.addAttribute("article", request);
        model.addAttribute("categories", new String[]{"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"});
        if (id != null) {
            model.addAttribute("id", id);
        }
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("isAdmin", hasRole("ROLE_ADMIN"));
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public String handleUploadException(Exception ex, HttpServletRequest request, Model model) {
        String errorMessage = "Ảnh đại diện vượt quá 5MB. Vui lòng chọn ảnh nhỏ hơn 5MB.";
        model.addAttribute("error", errorMessage);
        model.addAttribute("article", new HealthArticleRequest());
        model.addAttribute("categories", new String[]{"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"});
        model.addAttribute("isAdmin", hasRole("ROLE_ADMIN"));

        String uri = request.getRequestURI();
        boolean isEdit = uri != null && uri.endsWith("/edit");
        model.addAttribute("isEdit", isEdit);
        if (isEdit) {
            String[] segments = uri.split("/");
            if (segments.length > 0) {
                String idSegment = segments[segments.length - 2];
                if (idSegment.chars().allMatch(Character::isDigit)) {
                    model.addAttribute("id", Long.parseLong(idSegment));
                }
            }
        }

        return "article/form";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    /**
     * [DOCTOR, ADMIN] Danh sách quản lý bài viết
     * GET /articles/manage
     */
    @GetMapping
    public String manageList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "") String keyword,
            Model model) {

        User currentUser;
        try {
            currentUser = getLoggedInUser();
        } catch (Exception e) {
            log.error("Không lấy được người dùng hiện tại", e);
            return "redirect:/login";
        }

        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        boolean isAdmin = hasRole("ROLE_ADMIN");
        Page<HealthArticle> articles = articleService.getArticlesForManagement(status, keyword, pageable, currentUser, isAdmin);

        model.addAttribute("articles", articles);
        model.addAttribute("currentPage", page);
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("isAdmin", hasRole("ROLE_ADMIN"));
        model.addAttribute("canCreateArticle", !hasRole("ROLE_ADMIN"));

        return "article/manage-list";
    }

    /**
     * [DOCTOR, ADMIN] Trang tạo bài viết mới (GET)
     * GET /articles/manage/new
     */
    @GetMapping("/new")
    public String newArticleForm(Model model) {
        model.addAttribute("article", new HealthArticleRequest());
        model.addAttribute("isAdmin", hasRole("ROLE_ADMIN"));
        
        String[] categories = {"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"};
        model.addAttribute("categories", categories);

        return "article/form";
    }

    /**
     * [DOCTOR, ADMIN] Xử lý tạo bài viết mới (POST)
     * POST /articles/manage/new
     */
    @PostMapping("/new")
    public String createArticle(
            @Valid @ModelAttribute("article") HealthArticleRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            populateArticleFormModel(model, request, null, false);
            return "article/form";
        }

        try {
            User createdBy = getLoggedInUser();
            ArticleStatus parsedStatus = ArticleStatus.fromString(request.getStatus());
            validateStatusForCurrentUser(parsedStatus, null, createdBy);
            boolean hasUnsupportedEmbeddedContent = containsUnsupportedEmbeddedContent(request.getContent());
            articleService.createArticle(request, createdBy.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Tạo bài viết thành công.");
            return "redirect:/articles/manage";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            populateArticleFormModel(model, request, null, false);
            return "article/form";
        }
    }

    /**
     * [DOCTOR, ADMIN] Trang sửa bài viết (GET)
     * GET /articles/manage/{id}/edit
     */
    @GetMapping("/{id}/edit")
    public String editArticleForm(
            @PathVariable Long id,
            Model model) {

        Optional<HealthArticle> article = articleService.getArticleById(id);

        if (article.isEmpty()) {
            return "redirect:/articles/manage";
        }

        User currentUser;
        try {
            currentUser = getLoggedInUser();
        } catch (Exception e) {
            log.error("Không lấy được người dùng hiện tại", e);
            return "redirect:/articles/manage";
        }
        checkOwnership(article.get(), currentUser);

        HealthArticle a = article.get();
        HealthArticleRequest request = new HealthArticleRequest();
        request.setTitle(a.getTitle());
        request.setSummary(a.getSummary());
        request.setContent(a.getContent());
        request.setThumbnailUrl(a.getThumbnailUrl());
        request.setCategory(a.getCategory());
        request.setStatus(a.getStatus() != null ? a.getStatus().getValue() : null);

        model.addAttribute("article", request);
        model.addAttribute("id", id);
        model.addAttribute("isEdit", true);
        model.addAttribute("isAdmin", hasRole("ROLE_ADMIN"));
        
        String[] categories = {"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"};
        model.addAttribute("categories", categories);

        return "article/form";
    }

    /**
     * [DOCTOR, ADMIN] Xử lý sửa bài viết (POST)
     * POST /articles/manage/{id}/edit
     */
    @PostMapping("/{id}/edit")
    public String updateArticle(
            @PathVariable Long id,
            @Valid @ModelAttribute("article") HealthArticleRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        Optional<HealthArticle> existingArticle = articleService.getArticleById(id);
        if (existingArticle.isEmpty()) {
            return "redirect:/articles/manage";
        }

        User currentUser;
        try {
            currentUser = getLoggedInUser();
        } catch (Exception e) {
            log.error("Không lấy được người dùng hiện tại", e);
            return "redirect:/articles/manage";
        }
        checkOwnership(existingArticle.get(), currentUser);

        if (bindingResult.hasErrors()) {
            populateArticleFormModel(model, request, id, true);
            return "article/form";
        }

        try {
            HealthArticle existing = articleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết"));
            ArticleStatus parsedStatus = ArticleStatus.fromString(request.getStatus());
            validateStatusForCurrentUser(parsedStatus, existing.getStatus(), currentUser);
            articleService.updateArticle(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật bài viết thành công.");
            return "redirect:/articles/manage";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            populateArticleFormModel(model, request, id, true);
            return "article/form";
        }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String approveArticle(@PathVariable Long id) {
        try {
            articleService.updateArticleStatus(id, ArticleStatus.PUBLISHED, null);
        } catch (Exception e) {
            log.error("Duyệt bài viết thất bại, id={}", id, e);
        }
        return "redirect:/articles/manage";
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String rejectArticle(@PathVariable Long id, @RequestParam String reason) {
        if (reason == null || reason.isBlank()) {
            return "redirect:/articles/manage";
        }
        try {
            articleService.updateArticleStatus(id, ArticleStatus.REJECTED, reason.trim());
        } catch (Exception e) {
            log.error("Từ chối bài viết thất bại, id={}", id, e);
        }
        return "redirect:/articles/manage";
    }

    private void validateStatusForCurrentUser(ArticleStatus parsedStatus, ArticleStatus currentStatus, User currentUser) throws Exception {
        boolean isAdmin = currentUser.getRole().getName().equals("ROLE_ADMIN");
        if (!isAdmin && parsedStatus == ArticleStatus.PUBLISHED) {
            boolean alreadyPublished = (currentStatus == ArticleStatus.PUBLISHED);
            if (!alreadyPublished) {
                throw new Exception("Bác sĩ chỉ có thể lưu bài viết ở trạng thái Nháp hoặc Chờ duyệt");
            }
        }
    }

    /**
     * [DOCTOR, ADMIN] Xóa bài viết (POST)
     * POST /articles/manage/{id}/delete
     * Gửi request: POST và có confirm() ở client
     */
    @PostMapping("/{id}/delete")
    public String deleteArticle(@PathVariable Long id) {

        Optional<HealthArticle> existingArticle = articleService.getArticleById(id);
        if (existingArticle.isEmpty()) {
            return "redirect:/articles/manage";
        }

        User currentUser;
        try {
            currentUser = getLoggedInUser();
        } catch (Exception e) {
            log.error("Không lấy được người dùng hiện tại", e);
            return "redirect:/articles/manage";
        }

        try {
            checkOwnership(existingArticle.get(), currentUser);
            articleService.deleteArticle(id);
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Xóa bài viết thất bại, id={}", id, e);
        }

        return "redirect:/articles/manage";
    }
}
