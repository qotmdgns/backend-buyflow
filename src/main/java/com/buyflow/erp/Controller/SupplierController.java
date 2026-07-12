package com.buyflow.erp.Controller;

import com.buyflow.erp.Common.ApiResponse;
import com.buyflow.erp.Dto.SupplierFilterOptionsResponse;
import com.buyflow.erp.Dto.SupplierPageResponse;
import com.buyflow.erp.Dto.SupplierRequest;
import com.buyflow.erp.Dto.SupplierResponse;
import com.buyflow.erp.Dto.SupplierTradeStatusRequest;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.ExcelService;
import com.buyflow.erp.Service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;

import com.buyflow.erp.Entity.Users;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
@RestController
@RequiredArgsConstructor
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;
    private final ExcelService excelService;
    private final UserRepository userRepository;
    @GetMapping
    @PreAuthorize(SecurityExpressions.SUPPLIERS_READ)
    public ApiResponse<SupplierPageResponse> search(
            @RequestParam(name = "supplierCode", required = false) String supplierCode,
            @RequestParam(name = "supplierName", required = false) String supplierName,
            @RequestParam(name = "manager", required = false) String manager,
            @RequestParam(name = "tradeStatus", required = false) String tradeStatus,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "15") int size
    ) {
        return ApiResponse.success(
                "Supplier list loaded.",
                supplierService.search(supplierCode, supplierName, manager, tradeStatus, page, size)
        );
    }

    @GetMapping("/filter-options")
    @PreAuthorize(SecurityExpressions.SUPPLIERS_READ)
    public ApiResponse<SupplierFilterOptionsResponse> findFilterOptions() {
        return ApiResponse.success("Supplier filter options loaded.", supplierService.findFilterOptions());
    }

    @GetMapping("/business-number/exists")
    @PreAuthorize(SecurityExpressions.SUPPLIERS_READ)
    public ApiResponse<Boolean> existsBusinessNumber(
            @RequestParam(name = "businessNumber") String businessNumber,
            @RequestParam(name = "excludeSupplierId", required = false) Long excludeSupplierId
    ) {
        return ApiResponse.success(
                "Supplier business number duplication checked.",
                supplierService.existsBusinessNumber(businessNumber, excludeSupplierId)
        );
    }
    
    @GetMapping("/excel")
    @PreAuthorize(SecurityExpressions.SUPPLIERS_READ)
    public void exportExcel(HttpServletResponse response, Authentication authentication) throws IOException {
        excelService.exportExcel("suppliers", getCurrentUser(authentication), response);
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize(SecurityExpressions.SUPPLIERS_READ)
    public ApiResponse<SupplierResponse> findById(@PathVariable(name = "supplierId") Long supplierId) {
        return ApiResponse.success("Supplier detail loaded.", supplierService.findById(supplierId));
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.SUPPLIERS_WRITE)
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ApiResponse.success("Supplier created.", supplierService.create(request));
    }

    @PutMapping("/{supplierId}")
    @PreAuthorize(SecurityExpressions.SUPPLIERS_WRITE)
    public ApiResponse<SupplierResponse> update(
            @PathVariable(name = "supplierId") Long supplierId,
            @Valid @RequestBody SupplierRequest request
    ) {
        return ApiResponse.success("Supplier updated.", supplierService.update(supplierId, request));
    }

    @PatchMapping("/{supplierId}/trade-status")
    @PreAuthorize(SecurityExpressions.SUPPLIERS_WRITE)
    public ApiResponse<SupplierResponse> changeTradeStatus(
            @PathVariable(name = "supplierId") Long supplierId,
            @Valid @RequestBody SupplierTradeStatusRequest request
    ) {
        return ApiResponse.success(
                "Supplier trade status changed.",
                supplierService.changeTradeStatus(supplierId, request.tradeStatus())
        );
    }

    @DeleteMapping("/{supplierId}")
    @PreAuthorize(SecurityExpressions.SUPPLIERS_WRITE)
    public ApiResponse<Void> delete(@PathVariable(name = "supplierId") Long supplierId) {
        supplierService.delete(supplierId);
        return ApiResponse.success("Supplier deleted.");
    }

    private Users getCurrentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Users user && user.getUserId() != null) {
            return userRepository.findById(user.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "Current user was not found"));
        }

        String loginValue = principal instanceof UserDetails userDetails
                ? userDetails.getUsername()
                : authentication.getName();

        return userRepository.findByLoginId(loginValue)
                .orElseGet(() -> {
                    try {
                        return userRepository.findById(Long.valueOf(loginValue))
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Current user was not found"));
                    } catch (NumberFormatException error) {
                        throw new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Current user was not found");
                    }
                });
    }
}
