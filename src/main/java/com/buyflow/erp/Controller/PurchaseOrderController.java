package com.buyflow.erp.Controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.PurchaseOrderDto;
import com.buyflow.erp.Dto.PurchaseRequestDto;
import com.buyflow.erp.Dto.WarehouseDto;
import com.buyflow.erp.Entity.Attachment;
import com.buyflow.erp.Entity.Supplier;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Repository.AttachmentRepository;

import com.buyflow.erp.Repository.SupplierRepository;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.AttachmentAuthorizationService;
import com.buyflow.erp.Service.ExcelService;
import com.buyflow.erp.Service.FileService;
import com.buyflow.erp.Service.PurchaseOrderService;
import com.buyflow.erp.Service.PurchaseRequestService;
import com.buyflow.erp.Service.WarehouseService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/orders", "/api/orders"})
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;
    private final WarehouseService warehouseService;
    private final PurchaseRequestService purchaseRequestService;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final AttachmentRepository attachmentRepository;
    private final ExcelService excelService;
    private final AttachmentAuthorizationService attachmentAuthorizationService;

    @GetMapping("/filter-options")
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_READ)
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("statuses", Arrays.asList("전체", "ORDERED", "CONFIRMED", "CANCELLED"));

        List<Map<String, Object>> suppliers = supplierRepository.findAll().stream()
                .map(supplier -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("supplierId", supplier.getSupplierId());
                    map.put("supplierName", supplier.getSupplierName());
                    return map;
                })
                .collect(Collectors.toList());
        options.put("suppliers", suppliers);

        return ResponseEntity.ok(options);
    }
    
    @GetMapping("/form-options")
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_WRITE)
    public ResponseEntity<Map<String, Object>> getFormOptions() {
        Map<String, Object> options = new HashMap<>();
        

        options.put("statuses", Arrays.asList("전체", "ORDERED", "CONFIRMED", "CANCELLED"));

        options.put("statuses", Arrays.asList("전체", "PENDING", "ORDERED", "CONFIRMED", "CANCELLED"));
        
        // 1. DB에서 엔티티 원본을 가져옵니다.

        List<Supplier> actualSuppliers = supplierRepository.findAll(); 
        List<Map<String, Object>> robustSuppliers = new ArrayList<>();
        
        for (Supplier supplier : actualSuppliers) {
            Map<String, Object> map = new HashMap<>();
            map.put("supplierId", supplier.getSupplierId());
            map.put("supplierName", supplier.getSupplierName());
            map.put("manager", supplier.getManager() != null ? supplier.getManager() : "-");
            map.put("contact", supplier.getContact() != null ? supplier.getContact() : "-");
            
            robustSuppliers.add(map);
        }
        
        options.put("suppliers", robustSuppliers);
        List<PurchaseRequestDto.ListResponse> approvedRequests = 
                purchaseRequestService.getApprovedRequestsWithoutPaging();
        options.put("approvedPurchaseRequests", approvedRequests);

        List<WarehouseDto.HouseList> actualWarehouses = warehouseService.findAllWarehouses().stream()
                .filter(w -> "Y".equals(w.getUseYn()))
                .collect(Collectors.toList());
        options.put("warehouses", actualWarehouses);
        
        return ResponseEntity.ok(options);
    }
    
    @GetMapping("/purchase-requests/{requestId}/items")
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_WRITE)
    public ResponseEntity<List<PurchaseOrderDto.ItemResponse>> getRequestItems(
    		@PathVariable(name = "requestId") Long requestId) {
    	List<PurchaseOrderDto.ItemResponse> items = service.getApprovedRequestItems(requestId);
    	
    	return ResponseEntity.ok(items);
    }
    @GetMapping("/{orderId}")
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_READ)
    public ResponseEntity<PurchaseOrderDto.Response> getOrder(
    		@PathVariable(name= "orderId") Long orderId) { 
    	PurchaseOrderDto.Response response = service.getOrderWithItems(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_READ)
    public ResponseEntity<PageResponse<PurchaseOrderDto.Response>> getOrderList(
        PurchaseOrderDto.SearchCondition condition) {
        
        PageResponse<PurchaseOrderDto.Response> response = service.getOrderList(condition);
        return ResponseEntity.ok(response);
    }

    
    // 3. 발주 등록

    @PostMapping
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_WRITE)
    public ResponseEntity<PurchaseOrderDto.Response> createOrder(
    		@RequestPart("data") PurchaseOrderDto.Request request,
    		@RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) throws Exception{
        Users currentUser = getCurrentUser(authentication);
        request.setCreatedBy(currentUser.getUserId());
        request.setUserName(currentUser.getUserName());
        
    	if (file != null && !file.isEmpty()) {
    		Attachment savedFile = fileService.uploadFile(file, currentUser.getUserId(), currentUser.getUserName());
            if (savedFile != null) {
    		    request.setAttachmentId(savedFile.getAttachmentId());
            }
    	}
        PurchaseOrderDto.Response response = service.createOrder(request);
        return ResponseEntity.ok(response);
    }
    @PutMapping("/{orderId}")
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_WRITE) 
    public ResponseEntity<PurchaseOrderDto.Response> updateOrder(
            @PathVariable(name = "orderId") Long orderId,
            @RequestPart("data") PurchaseOrderDto.Request request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) throws Exception {
        Users currentUser = getCurrentUser(authentication);
        request.setCreatedBy(currentUser.getUserId());
        request.setUserName(currentUser.getUserName());
    	
    	if (file != null && !file.isEmpty()) {
    		Attachment savedFile = fileService.uploadFile(file, currentUser.getUserId(), currentUser.getUserName());
            if (savedFile != null) {
    		    request.setAttachmentId(savedFile.getAttachmentId());
            }
    	}

        PurchaseOrderDto.Response response = service.updateOrder(orderId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{orderId}/cancel")
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_WRITE)
    public ResponseEntity<PurchaseOrderDto.Response> cancelOrder(
            @PathVariable(name = "orderId") Long orderId,
            @RequestBody Map<String, String> request) {
        
        String cancelReason = request.get("cancelReason");
        
        PurchaseOrderDto.Response response = service.cancelOrder(orderId, cancelReason);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/excel")
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_READ)
    public void exportExcel(HttpServletResponse response, Authentication authentication) throws IOException {
    	excelService.exportExcel("orders", getCurrentUser(authentication), response);
    }

    private Users getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("권한이 없습니다.");
        }

        return userRepository.findByLoginId(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("권한이 없습니다."));
    }
    
    @GetMapping("/attachments/download/{attachmentId}")
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_READ)
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("attachmentId") Long attachmentId,
            Authentication authentication) {
        try {
            Attachment attachment = attachmentRepository.findById(attachmentId)
                    .orElseThrow(() -> new RuntimeException("파일 정보를 찾을 수 없습니다."));
            attachmentAuthorizationService.assertCanDownload(authentication, attachment);

            Path filePath = Paths.get(attachment.getFilePath());

            if (!Files.exists(filePath)) {
                throw new RuntimeException("서버에 실제 파일이 존재하지 않습니다.");
            }
            Resource resource = 
                    new InputStreamResource(Files.newInputStream(filePath));
            String encodedFileName = UriUtils.encode(attachment.getOriginalName(), StandardCharsets.UTF_8);
            String contentDisposition = "attachment; filename=\"" + encodedFileName + "\"";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(filePath))
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
}
