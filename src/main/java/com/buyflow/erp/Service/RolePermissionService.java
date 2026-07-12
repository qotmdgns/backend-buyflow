package com.buyflow.erp.Service;

import com.buyflow.erp.Common.BusinessException;
import com.buyflow.erp.Common.ErrorCode;
import com.buyflow.erp.Entity.Permission;
import com.buyflow.erp.Entity.Role;
import com.buyflow.erp.Entity.RolePermission;
import com.buyflow.erp.Entity.User;
import com.buyflow.erp.Repository.AuthUserRepository;
import com.buyflow.erp.Repository.PermissionRepository;
import com.buyflow.erp.Repository.RolePermissionRepository;
import com.buyflow.erp.Repository.RoleRepository;
import com.buyflow.erp.Security.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 역할별 권한(ROLE_PERMISSIONS) 조회/저장 서비스.
 *
 * - 조회: 역할 코드로 현재 부여된 권한 코드 목록을 반환.
 * - 저장: 해당 역할의 기존 매핑을 모두 지우고, 전달받은 권한 코드로 새로 채운다(전량 교체).
 *   이렇게 하면 체크 해제된 권한은 자연스럽게 빠지고, 추가된 권한만 새로 들어간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuthUserRepository userRepository;
    private final RbacQueryService rbacQueryService;

    /** 역할 코드(ROLE_ADMIN 등)로 현재 부여된 권한 코드 목록 조회 */
    public List<String> findPermissionCodes(String roleCode) {
        Role role = getActiveRole(roleCode);

        List<Long> permissionIds = rolePermissionRepository.findByRoleId(role.getRoleId())
                .stream()
                .map(RolePermission::getPermissionId)
                .toList();

        if (permissionIds.isEmpty()) {
            return List.of();
        }

        return permissionRepository
                .findByPermissionIdInAndUseYnOrderByPermissionGroupAscPermissionCodeAsc(permissionIds, "Y")
                .stream()
                .map(Permission::getPermissionCode)
                .toList();
    }

    /** 역할의 권한을 전달받은 코드 목록으로 전량 교체 후, 저장된 코드 목록 반환 */
    @Transactional
    public List<String> replacePermissions(
            String roleCode,
            List<String> permissionCodes,
            String currentLoginId
    ) {
        requireCurrentAdmin(currentLoginId);
        Role role = getActiveRole(roleCode);
        Set<String> requestedCodes = new LinkedHashSet<>(permissionCodes == null
                ? List.of()
                : permissionCodes);

        List<Permission> permissions = requestedCodes.isEmpty()
                ? List.of()
                : permissionRepository.findByPermissionCodeInAndUseYn(requestedCodes, "Y");

        if (permissions.size() != requestedCodes.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid or inactive permission is included.");
        }

        // 1) 기존 매핑 전부 삭제 (UNIQUE 제약 충돌 방지를 위해 즉시 flush)
        rolePermissionRepository.deleteByRoleId(role.getRoleId());
        rolePermissionRepository.flush();

        // 2) 비어 있으면 여기서 종료 (모든 권한 해제 상태)
        if (permissions.isEmpty()) {
            return List.of();
        }

        // 3) 새 매핑 insert
        LocalDateTime now = LocalDateTime.now();
        List<RolePermission> rows = permissions.stream()
                .map(permission -> {
                    RolePermission rolePermission = new RolePermission();
                    rolePermission.setRoleId(role.getRoleId());
                    rolePermission.setPermissionId(permission.getPermissionId());
                    rolePermission.setCreatedAt(now);
                    return rolePermission;
                })
                .toList();

        rolePermissionRepository.saveAll(rows);

        return permissions.stream()
                .map(Permission::getPermissionCode)
                .sorted()
                .toList();
    }

    private void requireCurrentAdmin(String currentLoginId) {
        User user = userRepository.findByLoginId(currentLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        boolean admin = rbacQueryService.findRoleCodesByUserId(user.getUserId())
                .stream()
                .anyMatch(PermissionCodes.ADMIN_ROLE_CODE::equals);

        if (!admin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Administrator role is required.");
        }
    }

    private Role getActiveRole(String roleCode) {
        return roleRepository.findByRoleCodeAndUseYn(roleCode, "Y")
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "역할을 찾을 수 없습니다: " + roleCode));
    }
}
