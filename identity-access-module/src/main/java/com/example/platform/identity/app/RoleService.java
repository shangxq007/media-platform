package com.example.platform.identity.app;

import com.example.platform.identity.domain.Role;
import com.example.platform.identity.infrastructure.RoleRepository;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Role createRole(String roleKey, String name, String description, Role.RoleScope scope) {
        String id = Ids.newId("rol");
        Instant now = Instant.now();
        Role role = new Role(id, roleKey, name, description, scope, now);
        return roleRepository.save(role);
    }

    public Role getRoleByKey(String roleKey) {
        return roleRepository.findByKey(roleKey)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleKey));
    }

    public List<Role> listAllRoles() {
        return roleRepository.findAll();
    }

    public List<Role> listRolesByScope(Role.RoleScope scope) {
        return roleRepository.findByScope(scope);
    }
}
