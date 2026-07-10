package com.buyflow.erp.Controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.WarehouseDto;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.WarehouseService;

import lombok.RequiredArgsConstructor;

@CrossOrigin(origins= "http://localhost:3000")
@RestController
@RequiredArgsConstructor
@RequestMapping("/warehouses")
public class WarehouseController {
    private final WarehouseService warehouseService;

    // 목록 조회
    @GetMapping
    @PreAuthorize(SecurityExpressions.WAREHOUSES_READ)
    public ResponseEntity<PageResponse<WarehouseDto.HouseList>> getWarehouseList(
        WarehouseDto.SearchCondition condition) { // 파라미터가 자동으로 DTO 객체 안에 쏙 들어갑니다.
    
        PageResponse<WarehouseDto.HouseList> result = warehouseService.searchWarehouses(condition);
        return ResponseEntity.ok(result);
    }
    

    @GetMapping("/filter-options")
    @PreAuthorize(SecurityExpressions.WAREHOUSES_READ)
    public ResponseEntity<Map<String, List<String>>> getFilterOptions() {
        
        Map<String, List<String>> options = new HashMap<>();
        
        options.put("warehouseTypes", Arrays.asList("전체", "냉동 창고", "냉장 창고", "위험물 창고", "일반 창고", "보세 창고"));
        options.put("useYn", Arrays.asList("전체", "사용 중", "사용 중지"));
        
        return ResponseEntity.ok(options);
    }
    
    // 단건 조회
    @GetMapping("/{warehouseCode}")
    @PreAuthorize(SecurityExpressions.WAREHOUSES_READ)
    public ResponseEntity<WarehouseDto.Detail> getWarehouse(
    		@PathVariable(name = "warehouseCode") String warehouseCode) {
    	return ResponseEntity.ok(warehouseService.getWarehouse(warehouseCode));
    }
    
    // 창고 등록
    @PostMapping
    @PreAuthorize(SecurityExpressions.WAREHOUSES_WRITE)
    public ResponseEntity<WarehouseDto.Create> createWarehouse(
    		@RequestBody WarehouseDto.Create request) {
    	WarehouseDto.Create result = warehouseService.createWarehouse(request);
    	return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    
    // 창고 수정
    @PatchMapping("/{warehouseCode}")
    @PreAuthorize(SecurityExpressions.WAREHOUSES_WRITE)
    public ResponseEntity<WarehouseDto.Detail> updateWarehouse(
    		@PathVariable(name = "warehouseCode") String warehouseCode, 
    		@RequestBody WarehouseDto.Update request) {
    	WarehouseDto.Detail result = warehouseService.updateWarehouse(warehouseCode, request);
    	return ResponseEntity.ok(result);
    }
    
    // 창고 삭제
    @DeleteMapping("/{warehouseCode}")
    @PreAuthorize(SecurityExpressions.WAREHOUSES_WRITE)
    public ResponseEntity<Void> deleteWarehouse(@PathVariable(name = "warehouseCode") String warehouseCode) {
    	warehouseService.deleteWarehouse(warehouseCode);
    	return ResponseEntity.noContent().build();
    }
}
