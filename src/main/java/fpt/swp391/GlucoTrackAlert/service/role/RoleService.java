package fpt.swp391.GlucoTrackAlert.service.role;

import fpt.swp391.GlucoTrackAlert.dto.role.RoleRequest;
import fpt.swp391.GlucoTrackAlert.model.role.Role;
import java.util.List;

public interface RoleService {
    List<Role> getAllRoles();
    Role getRoleById(Long id) throws Exception;
    Role createRole(RoleRequest request) throws Exception;
    Role updateRole(Long id, RoleRequest request) throws Exception;
    void deleteRole(Long id) throws Exception;
}