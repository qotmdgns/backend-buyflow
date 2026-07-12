package com.buyflow.erp.Service;

import com.buyflow.erp.Common.BusinessException;
import com.buyflow.erp.Common.ErrorCode;
import com.buyflow.erp.Dto.DepartmentPermissionProfileResponse;
import com.buyflow.erp.Dto.DepartmentPermissionUpdateRequest;
import com.buyflow.erp.Entity.DepartmentPermission;
import com.buyflow.erp.Entity.Permission;
import com.buyflow.erp.Entity.User;
import com.buyflow.erp.Repository.AuthUserRepository;
import com.buyflow.erp.Repository.DepartmentPermissionRepository;
import com.buyflow.erp.Repository.PermissionRepository;
import com.buyflow.erp.Repository.UserDepartmentAuthorizationRepository;
import com.buyflow.erp.Security.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentPermissionService {

    private final AuthUserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final DepartmentPermissionRepository departmentPermissionRepository;
    private final UserDepartmentAuthorizationRepository userDepartmentAuthorizationRepository;
    private final RbacQueryService rbacQueryService;

    public List<DepartmentPermissionProfileResponse> findProfiles(String currentLoginId) {
        DepartmentAccess access = resolveAccess(currentLoginId);
        requireReadPermission(access);

        if (!access.admin()) {
            return List.of(buildProfile(requireOwnDepartment(access)));
        }

        return userRepository.findDistinctDepartmentNames()
                .stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .distinct()
                .map(this::buildProfile)
                .toList();
    }

    public List<String> findPermissionCodes(String departmentName, String currentLoginId) {
        DepartmentAccess access = resolveAccess(currentLoginId);
        requireReadPermission(access);
        String normalizedDepartmentName = requireAccessibleDepartment(access, departmentName);
        List<Long> permissionIds = departmentPermissionRepository
                .findPermissionIdsByDepartmentName(normalizedDepartmentName);

        if (permissionIds.isEmpty()) {
            return List.of();
        }

        return permissionRepository
                .findByPermissionIdInAndUseYnOrderByPermissionGroupAscPermissionCodeAsc(permissionIds, "Y")
                .stream()
                .map(Permission::getPermissionCode)
                .toList();
    }

    @Transactional
    public List<String> replacePermissions(
            String departmentName,
            DepartmentPermissionUpdateRequest request,
            String currentLoginId
    ) {
        DepartmentAccess access = resolveAccess(currentLoginId);
        requireWritePermission(access);
        String normalizedDepartmentName = requireAccessibleDepartment(access, departmentName);
        Set<String> requestedCodes = new LinkedHashSet<>(request.permissionCodes() == null
                ? List.of()
                : request.permissionCodes());

        List<Permission> permissions = requestedCodes.isEmpty()
                ? List.of()
                : permissionRepository.findByPermissionCodeInAndUseYn(requestedCodes, "Y");

        if (permissions.size() != requestedCodes.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid or inactive permission is included.");
        }

        if (!access.admin() && permissions.stream().anyMatch(this::isSystemPermission)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "System permissions can only be assigned by an administrator."
            );
        }

        List<Permission> permissionsToSave = access.admin()
                ? permissions
                : preserveExistingSystemPermissions(normalizedDepartmentName, permissions);

        departmentPermissionRepository.deleteByDepartmentName(normalizedDepartmentName);
        departmentPermissionRepository.flush();

        LocalDateTime now = LocalDateTime.now();
        List<DepartmentPermission> rows = permissionsToSave.stream()
                .map(permission -> {
                    DepartmentPermission departmentPermission = new DepartmentPermission();
                    departmentPermission.setDepartmentName(normalizedDepartmentName);
                    departmentPermission.setPermissionId(permission.getPermissionId());
                    departmentPermission.setCreatedAt(now);
                    return departmentPermission;
                })
                .toList();

        departmentPermissionRepository.saveAll(rows);

        return permissionsToSave.stream()
                .map(Permission::getPermissionCode)
                .sorted()
                .toList();
    }

    private List<Permission> preserveExistingSystemPermissions(
            String departmentName,
            List<Permission> requestedPermissions
    ) {
        List<Long> existingPermissionIds = departmentPermissionRepository
                .findPermissionIdsByDepartmentName(departmentName);
        List<Permission> existingSystemPermissions = existingPermissionIds.isEmpty()
                ? List.of()
                : permissionRepository
                        .findByPermissionIdInAndUseYnOrderByPermissionGroupAscPermissionCodeAsc(
                                existingPermissionIds,
                                "Y"
                        )
                        .stream()
                        .filter(this::isSystemPermission)
                        .toList();

        Map<Long, Permission> permissionsById = new LinkedHashMap<>();
        existingSystemPermissions.forEach(permission ->
                permissionsById.put(permission.getPermissionId(), permission));
        requestedPermissions.forEach(permission ->
                permissionsById.put(permission.getPermissionId(), permission));

        return List.copyOf(permissionsById.values());
    }

    private DepartmentPermissionProfileResponse buildProfile(String departmentName) {
        return new DepartmentPermissionProfileResponse(
                departmentName,
                userRepository.countActiveUsersByDepartmentName(departmentName),
                userDepartmentAuthorizationRepository.countActiveAuthorizedUsersByDepartmentName(
                        departmentName,
                        "Y"
                )
        );
    }

    private DepartmentAccess resolveAccess(String currentLoginId) {
        User user = userRepository.findByLoginId(currentLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Set<String> roleCodes = new LinkedHashSet<>(rbacQueryService.findRoleCodesByUserId(user.getUserId()));
        boolean admin = roleCodes.contains(PermissionCodes.ADMIN_ROLE_CODE);
        Set<String> permissionCodes = admin
                ? Set.of()
                : new LinkedHashSet<>(rbacQueryService.findPermissionCodesByUserId(user.getUserId()));

        return new DepartmentAccess(user, admin, permissionCodes);
    }

    private void requireReadPermission(DepartmentAccess access) {
        if (access.admin()
                || access.permissionCodes().contains(PermissionCodes.ROLES_READ)
                || access.permissionCodes().contains(PermissionCodes.ROLES_WRITE)
                || access.permissionCodes().contains(PermissionCodes.LEGACY_ROLE_MANAGE)) {
            return;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN, "Role read permission is required.");
    }

    private void requireWritePermission(DepartmentAccess access) {
        if (access.admin()
                || access.permissionCodes().contains(PermissionCodes.ROLES_WRITE)
                || access.permissionCodes().contains(PermissionCodes.LEGACY_ROLE_MANAGE)) {
            return;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN, "Role write permission is required.");
    }

    private String requireAccessibleDepartment(DepartmentAccess access, String requestedDepartmentName) {
        String normalizedDepartmentName = requireDepartmentName(requestedDepartmentName);
        if (access.admin()) {
            return normalizedDepartmentName;
        }

        String ownDepartmentName = requireOwnDepartment(access);
        if (!ownDepartmentName.equals(normalizedDepartmentName)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only your own department can be managed.");
        }

        return normalizedDepartmentName;
    }

    private String requireOwnDepartment(DepartmentAccess access) {
        String departmentName = normalizeText(access.user().getDepartmentName());
        if (!StringUtils.hasText(departmentName)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "A department is required to manage department permissions."
            );
        }

        return departmentName;
    }

    private boolean isSystemPermission(Permission permission) {
        return PermissionCodes.SYSTEM_PERMISSION_GROUP.equalsIgnoreCase(permission.getPermissionGroup());
    }

    private String requireDepartmentName(String departmentName) {
        String normalized = normalizeText(departmentName);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Department name is required.");
        }

        return normalized;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private record DepartmentAccess(User user, boolean admin, Set<String> permissionCodes) {
    }
}
