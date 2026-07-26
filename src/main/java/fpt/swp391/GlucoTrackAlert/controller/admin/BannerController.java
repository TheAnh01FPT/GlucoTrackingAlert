package fpt.swp391.GlucoTrackAlert.controller.admin;

import fpt.swp391.GlucoTrackAlert.model.banner.Banner;
import fpt.swp391.GlucoTrackAlert.repository.banner.BannerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequestMapping("/admin/banners")
public class BannerController {

    private final BannerRepository bannerRepository;

    @Autowired
    public BannerController(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    // 1. Hiển thị trang danh sách tích hợp Modal (Có phân trang)
    @GetMapping
    public String listBanners(@RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Banner> bannerPage = bannerRepository.findAllByOrderByDisplayOrderAsc(pageable);

        model.addAttribute("banners", bannerPage.getContent());
        model.addAttribute("totalPages", bannerPage.getTotalPages());
        model.addAttribute("totalItems", bannerPage.getTotalElements());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);

        // Cung cấp một đối tượng Banner trống để Form Thêm mới trong Modal binding dữ
        // liệu
        if (!model.containsAttribute("banner")) {
            model.addAttribute("banner", new Banner());
        }

        return "admin/banner/list";
    }

    // 2. API bổ sung quan trọng: Lấy dữ liệu Banner dưới dạng JSON khi bấm nút
    // "Sửa" để đổ vào Modal via JS
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Banner> getBannerJson(@PathVariable Long id) {
        return bannerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. Xử lý Lưu (Thêm mới hoặc Cập nhật) từ Modal
    @PostMapping("/save")
    public String saveBanner(@ModelAttribute Banner banner,
            @RequestParam(value = "status", required = false) Boolean status,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {

        banner.setStatus(Boolean.TRUE.equals(status));

        // Validate dữ liệu đầu vào, nếu lỗi trả về trang danh sách kèm thông báo lỗi
        // hiển thị trên Modal/Toast
        if (banner.getTitle() == null || banner.getTitle().trim().isEmpty() || banner.getTitle().length() > 150) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tiêu đề không được để trống và tối đa 150 ký tự!");
            return "redirect:/admin/banners";
        }
        if (banner.getSubtitle() != null && banner.getSubtitle().length() > 500) {
            redirectAttributes.addFlashAttribute("errorMessage", "Phụ đề tối đa 500 ký tự!");
            return "redirect:/admin/banners";
        }
        if (banner.getRedirectUrl() != null && !banner.getRedirectUrl().trim().isEmpty()) {
            String url = banner.getRedirectUrl().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Đường dẫn (URL) phải bắt đầu bằng http:// hoặc https://");
                return "redirect:/admin/banners";
            }
            if (url.length() > 2048) {
                redirectAttributes.addFlashAttribute("errorMessage", "Đường dẫn (URL) tối đa 2048 ký tự!");
                return "redirect:/admin/banners";
            }
        }
        if (banner.getDisplayOrder() == null || banner.getDisplayOrder() < 1 || banner.getDisplayOrder() >= 10000) {
            redirectAttributes.addFlashAttribute("errorMessage", "Thứ tự hiển thị phải từ 1 đến 9999!");
            return "redirect:/admin/banners";
        }

        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                String originalFilename = imageFile.getOriginalFilename() != null
                        ? imageFile.getOriginalFilename().toLowerCase()
                        : "";
                if (!originalFilename.endsWith(".jpg") && !originalFilename.endsWith(".jpeg") &&
                        !originalFilename.endsWith(".png") && !originalFilename.endsWith(".webp")) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Chỉ cho phép upload file ảnh định dạng .jpg, .jpeg, .png, .webp!");
                    return "redirect:/admin/banners";
                }
                if (imageFile.getSize() > 5 * 1024 * 1024) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Kích thước file không được vượt quá 5MB!");
                    return "redirect:/admin/banners";
                }

                String uploadDir = "uploads/banners/";
                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                String filename = UUID.randomUUID().toString() + "_" + imageFile.getOriginalFilename();
                Path filePath = uploadPath.resolve(filename);
                Files.copy(imageFile.getInputStream(), filePath);
                banner.setImageUrl("/" + uploadDir + filename);
            } else if (banner.getId() != null) {
                Banner existing = bannerRepository.findById(banner.getId()).orElse(null);
                if (existing != null) {
                    banner.setImageUrl(existing.getImageUrl());
                }
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Thêm mới bắt buộc phải tải lên ảnh Banner!");
                return "redirect:/admin/banners";
            }

            if (banner.getImageUrl() == null || banner.getImageUrl().isEmpty()) {
                banner.setImageUrl("/images/default-banner.jpg");
            }

            bannerRepository.save(banner);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu banner thành công!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi upload ảnh: " + e.getMessage());
        }
        return "redirect:/admin/banners";
    }

    // 4. Xóa Banner
    @GetMapping("/delete/{id}")
    public String deleteBanner(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bannerRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa banner thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa banner!");
        }
        return "redirect:/admin/banners";
    }
}