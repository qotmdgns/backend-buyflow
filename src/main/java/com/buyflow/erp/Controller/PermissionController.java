package com.buyflow.erp.Controller;

import com.buyflow.erp.Common.ApiResponse;
import com.buyflow.erp.Dto.PermissionResponse;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize(SecurityExpressions.AUTHENTICATED)
    public ApiResponse<List<PermissionResponse>> findAll() {
        return ApiResponse.success("권한 목록 조회 성공", permissionService.findAll());
    }
}
