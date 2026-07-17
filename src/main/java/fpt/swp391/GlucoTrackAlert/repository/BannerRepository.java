package fpt.swp391.GlucoTrackAlert.repository;

import fpt.swp391.GlucoTrackAlert.model.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findByStatusOrderByDisplayOrderAsc(Boolean status);
    List<Banner> findAllByOrderByDisplayOrderAsc();
    Page<Banner> findAllByOrderByDisplayOrderAsc(Pageable pageable);
}
