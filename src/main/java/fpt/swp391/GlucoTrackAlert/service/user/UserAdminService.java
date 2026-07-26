package fpt.swp391.GlucoTrackAlert.service.user;

import fpt.swp391.GlucoTrackAlert.dto.user.UserAdminRequest;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import org.springframework.data.domain.Page;

import java.util.List;

public interface UserAdminService {
    List<User> getAllUsers();

    Page<User> getUsersPaged(int page, int size);

    Page<User> searchAndFilterUsersPaged(String email, String roleName, String status, int page, int size);

    long getDoctorCount();

    long getPatientCount();

    List<User> getUsersFilteredByRole(Long roleId);

    User getUserById(Long id) throws Exception;

    User createUserByAdmin(UserAdminRequest request) throws Exception;

    User updateUserByAdmin(Long id, UserAdminRequest request) throws Exception;

}
