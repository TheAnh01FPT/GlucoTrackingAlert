package fpt.swp391.GlucoTrackAlert.service.role;

import fpt.swp391.GlucoTrackAlert.model.role.Role;
import java.util.List;

public interface RoleService {
    List<Role> getAllRoles();
    Role getRoleById(Long id) throws Exception;
}