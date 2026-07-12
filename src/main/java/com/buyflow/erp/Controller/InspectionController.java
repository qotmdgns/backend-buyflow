package com.buyflow.erp.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.buyflow.erp.Dto.InspectionDto;
import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.InspectionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    @GetMapping("/pending")
    @PreAuthorize(SecurityExpressions.INSPECTIONS_READ)
    public ResponseEntity<PageResponse<InspectionDto.Response>> getPendingInspections(
            InspectionDto.SearchCondition condition) {
        return ResponseEntity.ok(inspectionService.getPendingInspections(condition));
    }

    @GetMapping("/completed")
    @PreAuthorize(SecurityExpressions.INSPECTIONS_READ)
    public ResponseEntity<PageResponse<InspectionDto.Response>> getCompletedInspections(
        InspectionDto.SearchCondition condition) {
    return ResponseEntity.ok(inspectionService.getCompletedInspections(condition));
    }

    @GetMapping("/completed/summary")
    @PreAuthorize(SecurityExpressions.INSPECTIONS_READ)
    public ResponseEntity<InspectionDto.SummaryResponse> getCompletedInspectionSummary() {
    return ResponseEntity.ok(inspectionService.getCompletedInspectionSummary());
    }

    @GetMapping("/pending/filter-options")
    @PreAuthorize(SecurityExpressions.INSPECTIONS_READ)
    public ResponseEntity<Map<String, Object>> getInspectionFilterOptions() {
        return ResponseEntity.ok(inspectionService.getInspectionFilterOptions());
    }

    @GetMapping("/pending/summary")
    @PreAuthorize(SecurityExpressions.INSPECTIONS_READ)
    public ResponseEntity<InspectionDto.PendingSummaryResponse> getPendingSummary() {
    return ResponseEntity.ok(inspectionService.getInspectionSummary());
}

    @GetMapping("/{receiptId}")
    @PreAuthorize(SecurityExpressions.INSPECTIONS_READ)
    public ResponseEntity<InspectionDto.Response> getInspectionDetail(
            @PathVariable(name = "receiptId") Long receiptId) {
        return ResponseEntity.ok(inspectionService.getPendingInspectionDetail(receiptId));
    }

    @PostMapping("/{receiptId}/result")
    @PreAuthorize(SecurityExpressions.INSPECTIONS_PROCESS)
    public ResponseEntity<String> saveInspectionResult(
            @PathVariable(name = "receiptId") Long receiptId,
            @RequestBody InspectionDto.ResultRequest request) {

        inspectionService.saveInspectionResult(receiptId, request);
        return ResponseEntity.ok("검수 완료");
    }
}
