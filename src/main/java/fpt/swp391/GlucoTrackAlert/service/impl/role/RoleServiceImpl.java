package fpt.swp391.GlucoTrackAlert.service.impl.role;

import fpt.swp391.GlucoTrackAlert.dto.role.RoleRequest;
import fpt.swp391.GlucoTrackAlert.model.role.Role;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.service.role.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    public Role getRoleById(Long id) throws Exception {
        return roleRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy vai trò với ID: " + id));
    }

    @Override
    @Transactional
    public Role createRole(RoleRequest request) throws Exception {
        String formattedName = request.getName().trim().toUpperCase();

        if (roleRepository.findByName(formattedName).isPresent()) {
            throw new Exception("Tên vai trò '" + formattedName + "' đã tồn tại trong hệ thống.");
        }

        Role role = Role.builder()
                .name(formattedName)
                .description(request.getDescription())
                .build();

        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public Role updateRole(Long id, RoleRequest request) throws Exception {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy vai trò với ID: " + id));

        String formattedName = request.getName().trim().toUpperCase();

        // Nếu đổi sang tên mới, kiểm tra xem tên mới đã bị trùng với bản ghi khác chưa
        if (!role.getName().equalsIgnoreCase(formattedName) &&
                roleRepository.findByName(formattedName).isPresent()) {
            throw new Exception("Tên vai trò mới '" + formattedName + "' đã tồn tại trên hệ thống.");
        }

        role.setName(formattedName);
        role.setDescription(request.getDescription());

        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) throws Exception {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new Exception("Không tìm thấy vai trò với ID: " + id));
        try {
            roleRepository.delete(role);
        } catch (Exception e) {
            throw new Exception("Không thể xóa vai trò này vì đang có tài khoản người dùng liên kết.");
        }
    }
}