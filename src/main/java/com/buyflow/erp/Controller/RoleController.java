package com.buyflow.erp.Controller;

import com.buyflow.erp.Common.ApiResponse;
import com.buyflow.erp.Dto.RolePermissionUpdateRequest;
import com.buyflow.erp.Dto.RoleResponse;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.RolePermissionService;
import com.buyflow.erp.Service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    @GetMapping
    @PreAuthorize(SecurityExpressions.ROLES_READ)
    public ApiResponse<List<RoleResponse>> findAll() {
        return ApiResponse.success("역할 목록 조회 성공", roleService.findAll());
    }

    // [추가] 특정 역할의 현재 권한 코드 목록 조회
    // GET /api/roles/{roleCode}/permissions  ->  ["dashboard.read", "products.read", ...]
    @GetMapping("/{roleCode}/permissions")
    @PreAuthorize(SecurityExpressions.ROLES_READ)
    public ApiResponse<List<String>> findRolePermissions(@PathVariable(name = "roleCode") String roleCode) {
        return ApiResponse.success(
                "역할 권한 조회 성공",
                rolePermissionService.findPermissionCodes(roleCode)
        );
    }

    // [추가] 특정 역할의 권한을 전량 교체(저장)
    // PUT /api/roles/{roleCode}/permissions   body: { "permissionCodes": ["...", "..."] }
    @PutMapping("/{roleCode}/permissions")
    @PreAuthorize(SecurityExpressions.ADMIN)
    public ApiResponse<List<String>> updateRolePermissions(
            @PathVariable(name = "roleCode") String roleCode,
            @RequestBody RolePermissionUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "역할 권한 저장 성공",
                rolePermissionService.replacePermissions(
                        roleCode,
                        request.permissionCodes(),
                        authentication.getName()
                )
        );
    }
}
