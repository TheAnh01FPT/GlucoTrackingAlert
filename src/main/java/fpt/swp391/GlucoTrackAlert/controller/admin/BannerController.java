package fpt.swp391.GlucoTrackAlert.controller.admin;

import fpt.swp391.GlucoTrackAlert.model.Banner;
import fpt.swp391.GlucoTrackAlert.repository.BannerRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping
    public String listBanners(Model model) {
        model.addAttribute("banners", bannerRepository.findAllByOrderByDisplayOrderAsc());
        return "admin/banner/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        Banner banner = new Banner();
        banner.setDisplayOrder(1);
        banner.setStatus(true);
        model.addAttribute("banner", banner);
        return "admin/banner/form";
    }

    @PostMapping("/save")
    public String saveBanner(@ModelAttribute Banner banner,
                             @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                             RedirectAttributes redirectAttributes) {

        if (banner.getTitle() == null || banner.getTitle().trim().isEmpty() || banner.getTitle().length() > 150) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tiêu đề không được để trống và tối đa 150 ký tự!");
            return "redirect:/admin/banners" + (banner.getId() != null ? "/edit/" + banner.getId() : "/create");
        }
        if (banner.getSubtitle() != null && banner.getSubtitle().length() > 500) {
            redirectAttributes.addFlashAttribute("errorMessage", "Phụ đề tối đa 500 ký tự!");
            return "redirect:/admin/banners" + (banner.getId() != null ? "/edit/" + banner.getId() : "/create");
        }
        if (banner.getRedirectUrl() != null && !banner.getRedirectUrl().trim().isEmpty()) {
            String url = banner.getRedirectUrl().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                redirectAttributes.addFlashAttribute("errorMessage", "Đường dẫn (URL) phải bắt đầu bằng http:// hoặc https://");
                return "redirect:/admin/banners" + (banner.getId() != null ? "/edit/" + banner.getId() : "/create");
            }
            if (url.length() > 2048) {
                redirectAttributes.addFlashAttribute("errorMessage", "Đường dẫn (URL) tối đa 2048 ký tự!");
                return "redirect:/admin/banners" + (banner.getId() != null ? "/edit/" + banner.getId() : "/create");
            }
        }
        if (banner.getDisplayOrder() == null || banner.getDisplayOrder() < 1 || banner.getDisplayOrder() >= 10000) {
            redirectAttributes.addFlashAttribute("errorMessage", "Thứ tự hiển thị phải từ 1 đến 9999!");
            return "redirect:/admin/banners" + (banner.getId() != null ? "/edit/" + banner.getId() : "/create");
        }

        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                String originalFilename = imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename().toLowerCase() : "";
                if (!originalFilename.endsWith(".jpg") && !originalFilename.endsWith(".jpeg") && 
                    !originalFilename.endsWith(".png") && !originalFilename.endsWith(".webp")) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Chỉ cho phép upload file ảnh định dạng .jpg, .jpeg, .png, .webp!");
                    return "redirect:/admin/banners" + (banner.getId() != null ? "/edit/" + banner.getId() : "/create");
                }
                if (imageFile.getSize() > 5 * 1024 * 1024) { // 5MB
                    redirectAttributes.addFlashAttribute("errorMessage", "Kích thước file không được vượt quá 5MB!");
                    return "redirect:/admin/banners" + (banner.getId() != null ? "/edit/" + banner.getId() : "/create");
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
                return "redirect:/admin/banners/create";
            }
            
            if (banner.getImageUrl() == null || banner.getImageUrl().isEmpty()) {
                banner.setImageUrl("/images/default-banner.jpg"); // fallback
            }

            bannerRepository.save(banner);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu banner thành công!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi upload ảnh: " + e.getMessage());
        }
        return "redirect:/admin/banners";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Banner banner = bannerRepository.findById(id).orElse(null);
        if (banner == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy banner!");
            return "redirect:/admin/banners";
        }
        model.addAttribute("banner", banner);
        return "admin/banner/form";
    }

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
