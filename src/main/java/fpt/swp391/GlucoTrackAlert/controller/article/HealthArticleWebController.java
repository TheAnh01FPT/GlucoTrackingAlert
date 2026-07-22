package fpt.swp391.GlucoTrackAlert.controller.article;

import fpt.swp391.GlucoTrackAlert.model.article.HealthArticle;
import fpt.swp391.GlucoTrackAlert.service.article.HealthArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Controller công khai xử lý hiển thị danh sách bài viết và chi tiết bài viết
 * Khách vãng lai và Bệnh nhân chỉ xem bài published
 */
@Controller
@RequestMapping("/articles")
@RequiredArgsConstructor
public class HealthArticleWebController {

    private final HealthArticleService articleService;
    private static final int PAGE_SIZE = 9;

    /**
     * [PUBLIC] Danh sách bài viết công khai
     * GET /articles
     * Query params: page (0-indexed, default 0), keyword, category
     */
    @GetMapping
    public String listArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String category,
            Model model) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<HealthArticle> articles = articleService.getPublishedArticles(keyword, category, pageable);

        model.addAttribute("articles", articles);
        model.addAttribute("currentPage", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);

        // Danh sách danh mục cố định
        String[] categories = {"Dinh dưỡng", "Biến chứng", "Lối sống", "Thuốc điều trị", "Tin tức y khoa"};
        model.addAttribute("categories", categories);

        return "article/list";
    }

    /**
     * [PUBLIC] Chi tiết bài viết theo slug
     * GET /articles/{slug}
     * Nếu slug không tồn tại hoặc bài chưa published → redirect về /articles
     */
    @GetMapping("/{slug}")
    public String detailArticle(@PathVariable String slug, Model model) {

        Optional<HealthArticle> article = articleService.getArticleBySlug(slug);

        if (article.isEmpty()) {
            return "redirect:/articles";
        }

        model.addAttribute("article", article.get());
        return "article/detail";
    }
}
