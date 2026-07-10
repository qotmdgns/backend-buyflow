package com.buyflow.erp.Controller;

import com.buyflow.erp.Dto.ReceiptItemDto;
import com.buyflow.erp.Entity.ReceiptItem;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.ReceiptItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/receipt-items")
public class ReceiptItemController {

    private final ReceiptItemService receiptItemService;

    @GetMapping
    @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
    public List<ReceiptItem> getReceiptItems() {
        return receiptItemService.getReceiptItems();
    }

    @PostMapping
    @PreAuthorize(SecurityExpressions.RECEIPTS_WRITE)
    public ResponseEntity<String> saveReceiptItem(
            @RequestBody ReceiptItemDto.CreateRequest request) {
        receiptItemService.saveReceiptItem(request);
        return ResponseEntity.ok("저장 완료");
    }

    @PutMapping("/{receiptItemId}")
    @PreAuthorize(SecurityExpressions.RECEIPTS_WRITE)
    public ResponseEntity<String> updateReceiptItem(
            @PathVariable(name = "receiptItemId") Long receiptItemId,
            @RequestBody ReceiptItemDto.CreateRequest request) {
        receiptItemService.updateReceiptItem(receiptItemId, request);
        return ResponseEntity.ok("수정 완료");
    }

    @PutMapping("/{receiptItemId}/cancel")
    @PreAuthorize(SecurityExpressions.RECEIPTS_WRITE)
    public ResponseEntity<String> cancelReceiptItem(
            @PathVariable(name = "receiptItemId") Long receiptItemId) {
        receiptItemService.cancelReceiptItem(receiptItemId);
        return ResponseEntity.ok("취소 완료");
    }

    @GetMapping("/status/{receiptItemStatus}")
    @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
    public List<ReceiptItem> getReceiptItemsByStatus(
            @PathVariable(name = "receiptItemStatus") String receiptItemStatus) {
        return receiptItemService.getReceiptItemsByStatus(receiptItemStatus);
    }
}
