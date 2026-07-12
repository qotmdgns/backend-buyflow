package com.buyflow.erp.Service;

import com.buyflow.erp.Common.BusinessException;
import com.buyflow.erp.Common.ErrorCode;
import com.buyflow.erp.Entity.Permission;
import com.buyflow.erp.Entity.Role;
import com.buyflow.erp.Entity.User;
import com.buyflow.erp.Repository.AuthUserRepository;
import com.buyflow.erp.Repository.PermissionRepository;
import com.buyflow.erp.Repository.RolePermissionRepository;
import com.buyflow.erp.Repository.RoleRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private RbacQueryService rbacQueryService;

    @InjectMocks
    private RolePermissionService rolePermissionService;

    @Test
    void replacePermissionsRejectsInvalidCodesBeforeDeletingExistingMappings() {
        mockAdmin();
        Role role = new Role();
        role.setRoleId(1L);
        role.setRoleCode("VIEWER");

        Permission permission = new Permission();
        permission.setPermissionId(10L);
        permission.setPermissionCode("dashboard.read");

        when(roleRepository.findByRoleCodeAndUseYn("VIEWER", "Y"))
                .thenReturn(Optional.of(role));
        when(permissionRepository.findByPermissionCodeInAndUseYn(anyCollection(), eq("Y")))
                .thenReturn(List.of(permission));

        assertThatThrownBy(() -> rolePermissionService.replacePermissions(
                "VIEWER",
                List.of("dashboard.read", "typo.permission"),
                "admin"
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(rolePermissionRepository, never()).deleteByRoleId(anyLong());
        verify(rolePermissionRepository, never()).flush();
    }

    @Test
    void staleAdminJwtCannotReplacePermissionsAfterAdminRoleWasRemoved() {
        User user = new User();
        user.setUserId(1L);
        user.setLoginId("former-admin");
        when(userRepository.findByLoginId("former-admin")).thenReturn(Optional.of(user));
        when(rbacQueryService.findRoleCodesByUserId(1L)).thenReturn(List.of("VIEWER"));

        assertThatThrownBy(() -> rolePermissionService.replacePermissions(
                "VIEWER",
                List.of("dashboard.read"),
                "former-admin"
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(roleRepository, never()).findByRoleCodeAndUseYn(anyString(), anyString());
        verify(rolePermissionRepository, never()).deleteByRoleId(anyLong());
    }

    private void mockAdmin() {
        User admin = new User();
        admin.setUserId(99L);
        admin.setLoginId("admin");
        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        when(rbacQueryService.findRoleCodesByUserId(99L))
                .thenReturn(List.of(PermissionCodes.ADMIN_ROLE_CODE));
    }
}
