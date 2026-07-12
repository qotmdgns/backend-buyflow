package com.buyflow.erp.Controller;

import com.buyflow.erp.Common.ApiResponse;
import com.buyflow.erp.Dto.AdminUserDepartmentAuthorizationUpdateRequest;
import com.buyflow.erp.Dto.AdminUserProfileUpdateRequest;
import com.buyflow.erp.Dto.AdminUserResponse;
import com.buyflow.erp.Dto.AdminUserRoleUpdateRequest;
import com.buyflow.erp.Dto.AdminUserStatusUpdateRequest;
import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.RoleResponse;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize(SecurityExpressions.USERS_READ)
    public ApiResponse<List<AdminUserResponse>> findAll(Authentication authentication) {
        return ApiResponse.success(
                "관리자 사용자 목록 조회 성공",
                adminUserService.findAll(authentication.getName())
        );
    }

    @GetMapping("/page")
    @PreAuthorize(SecurityExpressions.USERS_READ)
    public ApiResponse<PageResponse<AdminUserResponse>> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "department", required = false) String department,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "useYn", required = false) String useYn,
            @RequestParam(name = "jobRank", required = false) String jobRank,
            @RequestParam(name = "roleCode", required = false) String roleCode,
            @RequestParam(name = "departmentAuthorized", required = false) String departmentAuthorized,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "관리자 사용자 목록 조회 성공",
                adminUserService.search(
                        keyword,
                        department,
                        status,
                        useYn,
                        jobRank,
                        roleCode,
                        departmentAuthorized,
                        page,
                        size,
                        authentication.getName()
                )
        );
    }

    @GetMapping("/{userId}")
    @PreAuthorize(SecurityExpressions.USERS_READ)
    public ApiResponse<AdminUserResponse> findById(
            @PathVariable(name = "userId") Long userId,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "관리자 사용자 상세 조회 성공",
                adminUserService.findById(userId, authentication.getName())
        );
    }

    @PatchMapping("/{userId}/approve")
    @PreAuthorize(SecurityExpressions.USERS_WRITE)
    public ApiResponse<AdminUserResponse> approve(
            @PathVariable(name = "userId") Long userId,
            Authentication authentication
    ) {
        return ApiResponse.success("사용자 승인 성공", adminUserService.approve(userId, authentication.getName()));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize(SecurityExpressions.USERS_WRITE)
    public ApiResponse<AdminUserResponse> updateStatus(
            @PathVariable(name = "userId") Long userId,
            @Valid @RequestBody AdminUserStatusUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "사용자 상태 수정 성공",
                adminUserService.updateStatus(userId, request, authentication.getName())
        );
    }

    @PutMapping("/{userId}/profile")
    @PreAuthorize(SecurityExpressions.USERS_WRITE)
    public ApiResponse<AdminUserResponse> updateProfile(
            @PathVariable(name = "userId") Long userId,
            @Valid @RequestBody AdminUserProfileUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "사용자 조직 정보 수정 성공",
                adminUserService.updateProfile(userId, request, authentication.getName())
        );
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize(SecurityExpressions.USERS_WRITE)
    public ApiResponse<AdminUserResponse> updateRoles(
            @PathVariable(name = "userId") Long userId,
            @Valid @RequestBody AdminUserRoleUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "사용자 역할 수정 성공",
                adminUserService.updateRoles(userId, request, authentication.getName())
        );
    }

    @PutMapping("/{userId}/department-authorization")
    @PreAuthorize(SecurityExpressions.USERS_WRITE)
    public ApiResponse<AdminUserResponse> updateDepartmentAuthorization(
            @PathVariable(name = "userId") Long userId,
            @Valid @RequestBody AdminUserDepartmentAuthorizationUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "User department authorization saved.",
                adminUserService.updateDepartmentAuthorization(userId, request, authentication.getName())
        );
    }

    @GetMapping("/departments")
    @PreAuthorize(SecurityExpressions.USERS_READ)
    public ApiResponse<List<String>> findDepartments(Authentication authentication) {
        return ApiResponse.success(
                "부서 목록 조회 성공",
                adminUserService.findDepartments(authentication.getName())
        );
    }

    @GetMapping("/assignable-roles")
    @PreAuthorize(SecurityExpressions.USERS_READ)
    public ApiResponse<List<RoleResponse>> findAssignableRoles(Authentication authentication) {
        return ApiResponse.success(
                "부여 가능 역할 목록 조회 성공",
                adminUserService.findAssignableRoles(authentication.getName())
        );
    }
}
