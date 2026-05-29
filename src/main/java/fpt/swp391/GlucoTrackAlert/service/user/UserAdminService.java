package fpt.swp391.GlucoTrackAlert.service.user;

import fpt.swp391.GlucoTrackAlert.dto.user.UserAdminRequest;
import fpt.swp391.GlucoTrackAlert.model.User;
import java.util.List;

public interface UserAdminService {
    List<User> getAllUsers();
    List<User> getUsersFilteredByRole(Long roleId);
    User getUserById(Long id) throws Exception;
    User createUserByAdmin(UserAdminRequest request) throws Exception;
    User updateUserByAdmin(Long id, UserAdminRequest request) throws Exception;
    void deleteUserByAdmin(Long id) throws Exception;
}