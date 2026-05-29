package fpt.swp391.GlucoTrackAlert.repository.user;

import fpt.swp391.GlucoTrackAlert.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // Tìm kiếm danh sách tài khoản lọc theo id vai trò cụ thể
    List<User> findByRoleId(Long roleId);
}