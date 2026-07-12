package com.buyflow.erp.Controller;

import com.buyflow.erp.Dto.RolePermissionUpdateRequest;
import com.buyflow.erp.Security.SecurityExpressions;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RoleControllerAuthorizationTest {

    @Test
    void globalRolePermissionUpdateIsAdminOnly() throws NoSuchMethodException {
        Method method = RoleController.class.getDeclaredMethod(
                "updateRolePermissions",
                String.class,
                RolePermissionUpdateRequest.class,
                Authentication.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(SecurityExpressions.ADMIN);
    }
}
