package com.buyflow.erp.Controller;

import com.buyflow.erp.Common.ApiResponse;
import com.buyflow.erp.Dto.DepartmentPermissionProfileResponse;
import com.buyflow.erp.Dto.DepartmentPermissionUpdateRequest;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.DepartmentPermissionService;
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
@RequestMapping("/admin/departments")
public class DepartmentPermissionController {

    private final DepartmentPermissionService departmentPermissionService;

    @GetMapping("/permission-profiles")
    @PreAuthorize(SecurityExpressions.ROLES_READ)
    public ApiResponse<List<DepartmentPermissionProfileResponse>> findProfiles(Authentication authentication) {
        return ApiResponse.success(
                "Department permission profiles loaded.",
                departmentPermissionService.findProfiles(authentication.getName())
        );
    }

    @GetMapping("/{departmentName}/permissions")
    @PreAuthorize(SecurityExpressions.ROLES_READ)
    public ApiResponse<List<String>> findPermissions(
            @PathVariable(name = "departmentName") String departmentName,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Department permissions loaded.",
                departmentPermissionService.findPermissionCodes(departmentName, authentication.getName())
        );
    }

    @PutMapping("/{departmentName}/permissions")
    @PreAuthorize(SecurityExpressions.ROLES_WRITE)
    public ApiResponse<List<String>> updatePermissions(
            @PathVariable(name = "departmentName") String departmentName,
            @RequestBody DepartmentPermissionUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Department permissions saved.",
                departmentPermissionService.replacePermissions(departmentName, request, authentication.getName())
        );
    }
}
