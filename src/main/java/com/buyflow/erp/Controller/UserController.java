package com.buyflow.erp.Controller;

import com.buyflow.erp.Common.ApiResponse;
import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.UserResponse;
import com.buyflow.erp.Dto.UserUpdateRequest;
import com.buyflow.erp.Security.PermissionCodes;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize(SecurityExpressions.USERS_READ)
    public ApiResponse<List<UserResponse>> findAll() {
        return ApiResponse.success("사용자 목록 조회 성공", userService.findAll());
    }

    @GetMapping("/page")
    @PreAuthorize(SecurityExpressions.USERS_READ)
    public ApiResponse<PageResponse<UserResponse>> search(
            @RequestParam(name= "keyword", required = false) String keyword,
            @RequestParam(name= "status", required = false) String status,
            @RequestParam(name= "useYn", required = false) String useYn,
            @RequestParam(name= "jobRank", required = false) String jobRank,
            @RequestParam(name= "page", defaultValue = "0") int page,
            @RequestParam(name= "size", defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                "사용자 목록 조회 성공",
                userService.search(keyword, status, useYn, jobRank, page, size)
        );
    }

    @GetMapping("/{userId}")
    @PreAuthorize(SecurityExpressions.USERS_READ)
    public ApiResponse<UserResponse> findById(@PathVariable(name = "userId") Long userId) {
        return ApiResponse.success("사용자 상세 조회 성공", userService.findById(userId));
    }

    @PutMapping("/{userId}")
    @PreAuthorize(SecurityExpressions.USERS_WRITE)
    public ApiResponse<UserResponse> update(
            @PathVariable(name = "userId") Long userId,
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "사용자 정보 수정 성공",
                userService.update(userId, request, authentication.getName(), canManageUsers(authentication))
        );
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize(SecurityExpressions.USERS_WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable(name = "userId") Long userId, Authentication authentication) {
        userService.deactivate(userId, authentication.getName(), canManageUsers(authentication));
    }

    private boolean canManageUsers(Authentication authentication) {
        return authentication != null && authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> PermissionCodes.ROLE_ADMIN.equals(authority)
                        || PermissionCodes.USERS_WRITE.equals(authority)
                        || PermissionCodes.LEGACY_USER_MANAGE.equals(authority));
    }
}
