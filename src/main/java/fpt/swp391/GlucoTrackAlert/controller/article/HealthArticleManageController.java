package fpt.swp391.GlucoTrackAlert.controller.article;

import fpt.swp391.GlucoTrackAlert.dto.article.HealthArticleRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.model.article.HealthArticle;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.service.article.HealthArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

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
    private static final int PAGE_SIZE = 10;

    /**
     * Lấy user đăng nhập hiện tại (tương tự PatientWebController.getLoggedInUser())
     */
    private User getLoggedInUser() throws Exception {
        String email = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new Exception("User không tồn tại"));
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

        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<HealthArticle> articles = articleService.getArticlesForManagement(status, keyword, pageable);

        model.addAttribute("articles", articles);
        model.addAttribute("currentPage", page);
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);

        return "article/manage-list";
    }

    /**
     * [DOCTOR, ADMIN] Trang tạo bài viết mới (GET)
     * GET /articles/manage/new
     */
    @GetMapping("/new")
    public String newArticleForm(Model model) {
        model.addAttribute("article", new HealthArticleRequest());
        
        
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
            @Valid @ModelAttribute HealthArticleRequest request,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("article", request);
            String[] categories = {"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"};
            model.addAttribute("categories", categories);
            return "article/form";
        }

        try {
            User createdBy = getLoggedInUser();
            articleService.createArticle(request, createdBy.getId());
            return "redirect:/articles/manage";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("article", request);
            
            String[] categories = {"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"};
            model.addAttribute("categories", categories);
            
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
            @Valid @ModelAttribute HealthArticleRequest request,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("article", request);
            model.addAttribute("id", id);
            model.addAttribute("isEdit", true);
            String[] categories = {"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"};
            model.addAttribute("categories", categories);
            return "article/form";
        }

        try {
            articleService.updateArticle(id, request);
            return "redirect:/articles/manage";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("article", request);
            model.addAttribute("id", id);
            model.addAttribute("isEdit", true);
            
            String[] categories = {"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"};
            model.addAttribute("categories", categories);
            
            return "article/form";
        }
    }

    /**
     * [DOCTOR, ADMIN] Xóa bài viết (POST)
     * POST /articles/manage/{id}/delete
     * Gửi request: POST và có confirm() ở client
     */
    @PostMapping("/{id}/delete")
    public String deleteArticle(@PathVariable Long id) {

        try {
            articleService.deleteArticle(id);
        } catch (Exception e) {
            log.error("Xóa bài viết thất bại, id={}", id, e);
        }

        return "redirect:/articles/manage";
    }
}
