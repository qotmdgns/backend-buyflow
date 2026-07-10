package com.buyflow.erp.Controller;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.buyflow.erp.Dto.InventoryAdjustmentRequest;
import com.buyflow.erp.Dto.InventoryAdjustmentResponse;
import com.buyflow.erp.Dto.StockDto;
import com.buyflow.erp.Dto.StockListResponse;
import com.buyflow.erp.Entity.Product;
import com.buyflow.erp.Entity.Stock;
import com.buyflow.erp.Repository.ProductRepository;
import com.buyflow.erp.Repository.StockRepository;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Repository.WarehouseRepository;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.InventoryService;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Service.ExcelService;

@RestController
@RequiredArgsConstructor
@RequestMapping({ "/stocks", "/inventories" })
public class StockController {

        private final StockRepository stockRepository;
        private final ProductRepository productRepository;
        private final WarehouseRepository warehouseRepository;
        private final UserRepository userRepository;
        private final InventoryService inventoryService;
        private final ExcelService excelService;

        @PostMapping("/{stockId}/adjustments")
        @PreAuthorize(SecurityExpressions.STOCK_ADJUST)
        public ResponseEntity<InventoryAdjustmentResponse> adjustStock(
                        @PathVariable(name = "stockId") Long stockId,
                        @RequestBody InventoryAdjustmentRequest request) {

                return ResponseEntity.ok(
                                inventoryService.adjustStock(stockId, request));
        }

       @GetMapping
       @PreAuthorize(SecurityExpressions.STOCK_READ)
public StockListResponse getInventories(
        @RequestParam(name = "itemCode", required = false) String itemCode,
        @RequestParam(name = "itemName", required = false) String itemName,
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "warehouseCode", required = false) String warehouseCode,
        @RequestParam(name = "stockStatus", required = false) String stockStatus) {

    // 카드(summary) 계산용 : 검색조건만 적용 (재고상태 제외)
    List<StockDto> summaryItems = stockRepository.findAll()
            .stream()
            .map(this::convert)

            .filter(item -> itemCode == null
                    || itemCode.isBlank()
                    || item.getItemCode().contains(itemCode))

            .filter(item -> itemName == null
                    || itemName.isBlank()
                    || item.getItemName().contains(itemName))

            .filter(item -> category == null
                    || category.isBlank()
                    || category.equals("전체")
                    || category.equals(item.getCategory()))

            .filter(item -> warehouseCode == null
                    || warehouseCode.isBlank()
                    || warehouseCode.equals("전체")
                    || warehouseCode.equals(item.getWarehouseCode()))

            .collect(Collectors.toList());

    // 목록 표시용 : 재고상태 필터까지 적용
    List<StockDto> items = summaryItems.stream()

            .filter(item -> {

                if (stockStatus == null
                        || stockStatus.isBlank()
                        || stockStatus.equals("전체")) {
                    return true;
                }

                if (stockStatus.equals("재고 없음")) {
                    return item.getCurrentStock() <= 0;
                }

                if (stockStatus.equals("안전재고 미만")) {
                    return item.getCurrentStock() > 0
                            && item.getCurrentStock() < item.getSafetyStock();
                }

                if (stockStatus.equals("정상")) {
                    return item.getCurrentStock() >= item.getSafetyStock();
                }

                return true;
            })

            .collect(Collectors.toList());

    long normal = summaryItems.stream()
            .filter(item -> item.getCurrentStock() > 0
                    && item.getCurrentStock() >= item.getSafetyStock())
            .count();

    long low = summaryItems.stream()
            .filter(item -> item.getCurrentStock() > 0
                    && item.getCurrentStock() < item.getSafetyStock())
            .count();

    long outOfStock = summaryItems.stream()
            .filter(item -> item.getCurrentStock() <= 0)
            .count();

    return StockListResponse.builder()
            .items(items)
            .summary(
                    StockListResponse.Summary.builder()
                            .total(summaryItems.size())
                            .normal((int) normal)
                            .low((int) low)
                            .outOfStock((int) outOfStock)
                            .build())
            .pagination(
                    StockListResponse.Pagination.builder()
                            .page(1)
                            .size(items.size())
                            .totalElements((long) items.size())
                            .totalPages(1)
                            .build())
            .build();
}

        @GetMapping("/filter-options")
        @PreAuthorize(SecurityExpressions.STOCK_READ)
        public Map<String, Object> getFilterOptions() {

                Map<String, Object> result = new HashMap<>();

                List<String> categories = new ArrayList<>();

                categories.add("전체");

                productRepository.findAll()
                                .stream()
                                .map(Product::getCategoryName)
                                .filter(category -> category != null && !category.isBlank())
                                .distinct()
                                .sorted()
                                .forEach(categories::add);

                result.put("categories", categories);
                List<Map<String, String>> warehouses = new ArrayList<>();

                warehouses.add(
                                Map.of(
                                                "value", "전체",
                                                "label", "전체"));

                warehouseRepository.findAll()
                                .forEach(warehouse -> {
                                        warehouses.add(
                                                        Map.of(
                                                                        "value", warehouse.getWarehouseCode(),
                                                                        "label", warehouse.getWarehouseName()));
                                });

                result.put("warehouses", warehouses);

                result.put(
                                "stockStatuses",
                                List.of(
                                                "전체",
                                                "정상",
                                                "안전재고 미만",
                                                "재고 없음"));
                result.put(
                                "movementTypes",
                                List.of(
                                                "전체",
                                                "INBOUND",
                                                "INSPECTION_ADJUST",
                                                "UPDATE",
                                                "DELETE",
                                                "CANCEL"));

                return result;
        }

        private StockDto convert(Stock stock) {

                var product = productRepository
                                .findById(stock.getProductId())
                                .orElse(null);

                var warehouse = warehouseRepository
                                .findById(stock.getWarehouseCode())
                                .orElse(null);

                return StockDto.builder()
                                .id(stock.getStockId())
                                .itemId(stock.getProductId())
                                .itemCode(
                                                product != null
                                                                ? product.getProductNo()
                                                                : "P-" + stock.getProductId())
                                .itemName(
                                                product != null
                                                                ? product.getProductName()
                                                                : "")
                                .category(
                                                product != null
                                                                ? product.getCategoryName()
                                                                : "")
                                .spec(
                                                product != null
                                                                ? product.getSpec()
                                                                : "")
                                .unit(
                                                product != null
                                                                ? product.getUnit()
                                                                : "EA")
                                .warehouseCode(stock.getWarehouseCode())
                                .warehouseName(
                                                warehouse != null
                                                                ? warehouse.getWarehouseName()
                                                                : "")
                                .currentStock(stock.getQuantity())
                                .safetyStock(
                                                stock.getSafetyStock() != null
                                                                ? stock.getSafetyStock()
                                                                : 0)
                                .lastChangedAt(
                                                stock.getUpdatedAt() != null
                                                                ? stock.getUpdatedAt()
                                                                                .format(DateTimeFormatter.ofPattern(
                                                                                                "yyyy-MM-dd HH:mm"))
                                                                : "")
                                .build();
        }

        @GetMapping("/excel")
        @PreAuthorize(SecurityExpressions.STOCK_READ)
        public void exportExcel(HttpServletResponse response, Authentication authentication) throws IOException {
                excelService.exportExcel(
                                "inventories",
                                getCurrentUser(authentication),
                                response);
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
