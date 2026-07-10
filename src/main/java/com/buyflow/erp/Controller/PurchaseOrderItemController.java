package com.buyflow.erp.Controller;

import com.buyflow.erp.Dto.PurchaseOrderItemDto;
import com.buyflow.erp.Entity.PurchaseOrderItem;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.PurchaseOrderItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/purchase-order-items")
public class PurchaseOrderItemController {

    private final PurchaseOrderItemService purchaseOrderItemService;

    @GetMapping
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_READ)
    public List<PurchaseOrderItem> getOrderItems() {
        return purchaseOrderItemService.getOrderItems();
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.PURCHASE_ORDERS_WRITE)
    public ResponseEntity<String> saveOrderItem(
            @RequestBody PurchaseOrderItemDto.CreateRequest request) {
        purchaseOrderItemService.saveOrderItem(request);
        return ResponseEntity.ok("저장 완료");
    }
}
