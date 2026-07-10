package com.buyflow.erp.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.buyflow.erp.Dto.StockHistoryResponseDto;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Repository.WarehouseRepository;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.StockHistoryService;

import lombok.RequiredArgsConstructor;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Service.ExcelService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stock-history")
public class StockHistoryController {

        private final StockHistoryService stockHistoryService;
        private final WarehouseRepository warehouseRepository;
        private final ExcelService excelService;
        private final UserRepository userRepository;

        @GetMapping
        @PreAuthorize(SecurityExpressions.STOCK_HISTORY_READ)
        public List<StockHistoryResponseDto> getStockHistory(
                        @RequestParam(name = "fromDate", required = false) String fromDate,
                        @RequestParam(name = "toDate", required = false) String toDate,
                        @RequestParam(name = "itemKeyword", required = false) String itemKeyword,
                        @RequestParam(name = "warehouseCode", required = false) String warehouseCode,
                        @RequestParam(name = "movementType", required = false) String movementType) {

                return stockHistoryService.searchStockHistory(
                                fromDate,
                                toDate,
                                itemKeyword,
                                warehouseCode,
                                movementType);
        }

        @GetMapping("/type/{historyType}")
        @PreAuthorize(SecurityExpressions.STOCK_HISTORY_READ)
        public List<StockHistoryResponseDto> getStockHistoryByType(
                        @PathVariable(name = "historyType") String historyType) {

                return stockHistoryService.getStockHistoryByType(historyType);
        }

        @GetMapping("/{historyId}")
        @PreAuthorize(SecurityExpressions.STOCK_HISTORY_READ)
        public StockHistoryResponseDto getStockHistory(
                        @PathVariable(name = "historyId") Long historyId) {

                return stockHistoryService.getStockHistory(historyId);
        }

        @GetMapping("/filter-options")
        @PreAuthorize(SecurityExpressions.STOCK_HISTORY_READ)
        public Map<String, Object> getFilterOptions() {

                Map<String, Object> result = new HashMap<>();

                result.put(
                                "warehouses",
                                warehouseRepository.findAll()
                                                .stream()
                                                .map(warehouse -> Map.of(
                                                                "value", warehouse.getWarehouseCode(),
                                                                "label", warehouse.getWarehouseName()))
                                                .toList());

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

        @GetMapping("/excel")
        @PreAuthorize(SecurityExpressions.STOCK_HISTORY_READ)
        public void exportExcel(HttpServletResponse response, Authentication authentication) throws IOException {
                excelService.exportExcel(
                                "stock-history",
                                getCurrentUser(authentication),
                                response);
        }

        private Users getCurrentUser(Authentication authentication) {
                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new AccessDeniedException("권한이 없습니다.");
                }

                return userRepository.findByLoginId(authentication.getName())
                                .orElseThrow(() -> new AccessDeniedException("권한이 없습니다."));
        }
}
