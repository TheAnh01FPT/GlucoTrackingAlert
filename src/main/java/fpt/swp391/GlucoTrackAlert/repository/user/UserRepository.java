package fpt.swp391.GlucoTrackAlert.repository.user;

import fpt.swp391.GlucoTrackAlert.model.user.User;
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

    boolean existsByPhone(String phone);

    // Count users by role name (e.g., DOCTOR, PATIENT)
    long countByRole_Name(String roleName);

    long countByRole_NameAndStatus(String roleName, String status);

    // Dùng khi cho phép 1 tài khoản pending_verification cập nhật lại SĐT của chính nó
    boolean existsByPhoneAndEmailNot(String phone, String email);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE " +
           "(:email IS NULL OR :email = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:roleName IS NULL OR :roleName = '' OR u.role.name = :roleName) AND " +
           "(:status IS NULL OR :status = '' OR u.status = :status)")
    List<User> searchAndFilterUsers(@org.springframework.data.repository.query.Param("email") String email, 
                                    @org.springframework.data.repository.query.Param("roleName") String roleName, 
                                    @org.springframework.data.repository.query.Param("status") String status);
}
