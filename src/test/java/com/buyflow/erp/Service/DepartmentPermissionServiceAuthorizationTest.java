package com.buyflow.erp.Service;

import com.buyflow.erp.Common.BusinessException;
import com.buyflow.erp.Common.ErrorCode;
import com.buyflow.erp.Dto.DepartmentPermissionProfileResponse;
import com.buyflow.erp.Dto.DepartmentPermissionUpdateRequest;
import com.buyflow.erp.Entity.Permission;
import com.buyflow.erp.Entity.User;
import com.buyflow.erp.Repository.AuthUserRepository;
import com.buyflow.erp.Repository.DepartmentPermissionRepository;
import com.buyflow.erp.Repository.PermissionRepository;
import com.buyflow.erp.Repository.UserDepartmentAuthorizationRepository;
import com.buyflow.erp.Security.PermissionCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentPermissionServiceAuthorizationTest {

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private DepartmentPermissionRepository departmentPermissionRepository;

    @Mock
    private UserDepartmentAuthorizationRepository userDepartmentAuthorizationRepository;

    @Mock
    private RbacQueryService rbacQueryService;

    @InjectMocks
    private DepartmentPermissionService departmentPermissionService;

    @Test
    void rolesWriteUserCanReplaceBusinessPermissionsForOwnDepartment() {
        User manager = user(1L, "manager", "Sales");
        Permission stockRead = permission(10L, "stock.read", "INVENTORY");
        mockAccess(manager, List.of("TEAM_MANAGER"), List.of(PermissionCodes.ROLES_WRITE));
        when(permissionRepository.findByPermissionCodeInAndUseYn(anyCollection(), eq("Y")))
                .thenReturn(List.of(stockRead));

        List<String> result = departmentPermissionService.replacePermissions(
                " Sales ",
                new DepartmentPermissionUpdateRequest(List.of("stock.read")),
                "manager"
        );

        assertThat(result).containsExactly("stock.read");
        verify(departmentPermissionRepository).deleteByDepartmentName("Sales");
        verify(departmentPermissionRepository).flush();
    }

    @Test
    void rolesWriteUserCannotReplaceAnotherDepartmentPermissions() {
        User manager = user(1L, "manager", "Sales");
        mockAccess(manager, List.of("TEAM_MANAGER"), List.of(PermissionCodes.ROLES_WRITE));

        assertForbidden(() -> departmentPermissionService.replacePermissions(
                "Logistics",
                new DepartmentPermissionUpdateRequest(List.of("stock.read")),
                "manager"
        ));

        verify(permissionRepository, never()).findByPermissionCodeInAndUseYn(anyCollection(), anyString());
        verify(departmentPermissionRepository, never()).deleteByDepartmentName(anyString());
    }

    @Test
    void staleJwtCannotWriteAfterRolesWriteWasRemovedFromDatabase() {
        User manager = user(1L, "manager", "Sales");
        mockAccess(manager, List.of("TEAM_MANAGER"), List.of(PermissionCodes.ROLES_READ));

        assertForbidden(() -> departmentPermissionService.replacePermissions(
                "Sales",
                new DepartmentPermissionUpdateRequest(List.of("stock.read")),
                "manager"
        ));

        verify(departmentPermissionRepository, never()).deleteByDepartmentName(anyString());
    }

    @Test
    void nonAdminCannotAssignSystemPermissionToDepartment() {
        User manager = user(1L, "manager", "Sales");
        Permission rolesWrite = permission(20L, PermissionCodes.ROLES_WRITE, PermissionCodes.SYSTEM_PERMISSION_GROUP);
        mockAccess(manager, List.of("TEAM_MANAGER"), List.of(PermissionCodes.ROLES_WRITE));
        when(permissionRepository.findByPermissionCodeInAndUseYn(anyCollection(), eq("Y")))
                .thenReturn(List.of(rolesWrite));

        assertForbidden(() -> departmentPermissionService.replacePermissions(
                "Sales",
                new DepartmentPermissionUpdateRequest(List.of(PermissionCodes.ROLES_WRITE)),
                "manager"
        ));

        verify(departmentPermissionRepository, never()).deleteByDepartmentName(anyString());
    }

    @Test
    void nonAdminBusinessUpdatePreservesExistingSystemPermissions() {
        User manager = user(1L, "manager", "Sales");
        Permission stockRead = permission(10L, "stock.read", "INVENTORY");
        Permission rolesWrite = permission(20L, PermissionCodes.ROLES_WRITE, PermissionCodes.SYSTEM_PERMISSION_GROUP);
        mockAccess(manager, List.of("TEAM_MANAGER"), List.of(PermissionCodes.ROLES_WRITE));
        when(permissionRepository.findByPermissionCodeInAndUseYn(anyCollection(), eq("Y")))
                .thenReturn(List.of(stockRead));
        when(departmentPermissionRepository.findPermissionIdsByDepartmentName("Sales"))
                .thenReturn(List.of(20L));
        when(permissionRepository.findByPermissionIdInAndUseYnOrderByPermissionGroupAscPermissionCodeAsc(
                List.of(20L),
                "Y"
        )).thenReturn(List.of(rolesWrite));

        List<String> result = departmentPermissionService.replacePermissions(
                "Sales",
                new DepartmentPermissionUpdateRequest(List.of("stock.read")),
                "manager"
        );

        assertThat(result).containsExactly(PermissionCodes.ROLES_WRITE, "stock.read");
        verify(departmentPermissionRepository).deleteByDepartmentName("Sales");
    }

    @Test
    void adminCanReplaceAnotherDepartmentWithSystemPermission() {
        User admin = user(99L, "admin", "Management");
        Permission rolesWrite = permission(20L, PermissionCodes.ROLES_WRITE, PermissionCodes.SYSTEM_PERMISSION_GROUP);
        mockAccess(admin, List.of(PermissionCodes.ADMIN_ROLE_CODE), List.of());
        when(permissionRepository.findByPermissionCodeInAndUseYn(anyCollection(), eq("Y")))
                .thenReturn(List.of(rolesWrite));

        List<String> result = departmentPermissionService.replacePermissions(
                "Sales",
                new DepartmentPermissionUpdateRequest(List.of(PermissionCodes.ROLES_WRITE)),
                "admin"
        );

        assertThat(result).containsExactly(PermissionCodes.ROLES_WRITE);
        verify(departmentPermissionRepository).deleteByDepartmentName("Sales");
    }

    @Test
    void nonAdminOnlyReceivesOwnDepartmentProfile() {
        User manager = user(1L, "manager", "Sales");
        mockAccess(manager, List.of("TEAM_MANAGER"), List.of(PermissionCodes.ROLES_READ));
        when(userRepository.countActiveUsersByDepartmentName("Sales")).thenReturn(7L);
        when(userDepartmentAuthorizationRepository.countActiveAuthorizedUsersByDepartmentName("Sales", "Y"))
                .thenReturn(4L);

        List<DepartmentPermissionProfileResponse> result = departmentPermissionService.findProfiles("manager");

        assertThat(result).containsExactly(new DepartmentPermissionProfileResponse("Sales", 7L, 4L));
        verify(userRepository, never()).findDistinctDepartmentNames();
    }

    @Test
    void nonAdminCannotReadAnotherDepartmentPermissions() {
        User manager = user(1L, "manager", "Sales");
        mockAccess(manager, List.of("TEAM_MANAGER"), List.of(PermissionCodes.ROLES_READ));

        assertForbidden(() -> departmentPermissionService.findPermissionCodes("Logistics", "manager"));

        verify(departmentPermissionRepository, never()).findPermissionIdsByDepartmentName(anyString());
    }

    private void mockAccess(User user, List<String> roleCodes, List<String> permissionCodes) {
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(rbacQueryService.findRoleCodesByUserId(user.getUserId())).thenReturn(roleCodes);
        if (!roleCodes.contains(PermissionCodes.ADMIN_ROLE_CODE)) {
            when(rbacQueryService.findPermissionCodesByUserId(user.getUserId())).thenReturn(permissionCodes);
        }
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private User user(Long id, String loginId, String departmentName) {
        User user = new User();
        user.setUserId(id);
        user.setLoginId(loginId);
        user.setDepartmentName(departmentName);
        return user;
    }

    private Permission permission(Long id, String code, String group) {
        Permission permission = new Permission();
        permission.setPermissionId(id);
        permission.setPermissionCode(code);
        permission.setPermissionGroup(group);
        return permission;
    }
}
