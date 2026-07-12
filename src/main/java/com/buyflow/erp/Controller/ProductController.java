package com.buyflow.erp.Controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.ProductDto;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.ExcelService;
import com.buyflow.erp.Service.ProductService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final ExcelService excelService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize(SecurityExpressions.PRODUCTS_WRITE)
    public ResponseEntity<String> saveProduct(
            @RequestBody ProductDto.CreateRequest request
    ) {
        productService.saveProduct(request);
        return ResponseEntity.ok("Product saved");
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.PRODUCTS_READ)
    public ResponseEntity<PageResponse<ProductDto.ListResponse>> getProducts(
            @ModelAttribute ProductDto.SearchCondition condition
    ) {
        return ResponseEntity.ok(productService.searchProducts(condition));
    }


    @GetMapping("/filter-options")
    @PreAuthorize(SecurityExpressions.PRODUCTS_READ)
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        return ResponseEntity.ok(productService.getFilterOptions());
    }

    


    @GetMapping("/excel")
    @PreAuthorize(SecurityExpressions.PRODUCTS_READ)
    public void downloadProductsExcel(
        @ModelAttribute ProductDto.SearchCondition condition,
        HttpServletResponse response
    ) throws IOException {
        List<ProductDto.ListResponse> products =
            productService.getProductsForExcel(condition);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("품목 관리");

            String[] headers = {
                    "품목 코드",
                    "품목명",
                    "카테고리",
                    "규격",
                    "단위",
                    "기준 단가",
                    "제조사",
                    "사용 여부",
                    "등록일",
                    "수정일"
            };

            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowNum = 1;

            for (ProductDto.ListResponse product : products) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(valueOrDash(product.getProductNo()));
                row.createCell(1).setCellValue(valueOrDash(product.getProductName()));
                row.createCell(2).setCellValue(valueOrDash(product.getCategoryName()));
                row.createCell(3).setCellValue(valueOrDash(product.getSpec()));
                row.createCell(4).setCellValue(valueOrDash(product.getUnit()));
                row.createCell(5).setCellValue(
                        product.getUnitPrice() != null ? product.getUnitPrice() : 0
                );
                row.createCell(6).setCellValue(valueOrDash(product.getCompanyName()));
                row.createCell(7).setCellValue(
                        "N".equalsIgnoreCase(product.getUseYn()) ? "미사용" : "사용"
                );
                row.createCell(8).setCellValue(valueOrDash(product.getCreatedAt()));
                row.createCell(9).setCellValue(valueOrDash(product.getUpdatedAt()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            String fileName = URLEncoder
                    .encode("품목관리.xlsx", StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            response.setContentType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );
            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename*=UTF-8''" + fileName
            );

            workbook.write(response.getOutputStream());
        }
    }

    private String valueOrDash(String value) {
    return value != null && !value.isBlank() ? value : "-";
}


    @GetMapping("/{productId}")
    @PreAuthorize(SecurityExpressions.PRODUCTS_READ)
    public ResponseEntity<ProductDto.ListResponse> getProduct(
            @PathVariable(name = "productId") Long productId
    ) {
        return ResponseEntity.ok(productService.getProduct(productId));
    }

    @PutMapping("/{productId}")
    @PreAuthorize(SecurityExpressions.PRODUCTS_WRITE)
    public ResponseEntity<String> updateProduct(
            @PathVariable(name = "productId") Long productId,
            @RequestBody ProductDto.CreateRequest request
    ) {
        productService.updateProduct(productId, request);
        return ResponseEntity.ok("Product updated");
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize(SecurityExpressions.PRODUCTS_WRITE)
    public ResponseEntity<String> deleteProduct(
            @PathVariable(name = "productId") Long productId
    ) {
        productService.deleteProduct(productId);
        return ResponseEntity.ok("Product deleted");
    }

    private Users getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

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
