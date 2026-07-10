package com.buyflow.erp.Controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.buyflow.erp.Dto.PageResponse;
import com.buyflow.erp.Dto.PurchaseRequestDto;
import com.buyflow.erp.Entity.Attachment;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Repository.AttachmentRepository;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.AttachmentAuthorizationService;
import com.buyflow.erp.Service.ExcelService;
import com.buyflow.erp.Service.PurchaseRequestService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/purchase-requests")
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;
    private final AttachmentRepository attachmentRepository;
    private final ExcelService excelService;
    private final UserRepository userRepository;
    private final AttachmentAuthorizationService attachmentAuthorizationService;

    @GetMapping
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_READ)
    public ResponseEntity<PageResponse<PurchaseRequestDto.ListResponse>> getPurchaseRequests(
            @RequestParam(name = "requestNumber", required = false, defaultValue = "") String requestNumber,
            @RequestParam(name = "title", required = false, defaultValue = "") String title,
            @RequestParam(name = "requester", required = false, defaultValue = "") String requester,
            @RequestParam(name = "department", required = false, defaultValue = "전체 부서") String department,
            @RequestParam(name = "status", required = false, defaultValue = "전체") String status,
            @RequestParam(name = "priority", required = false, defaultValue = "전체") String priority,
            @RequestParam(name = "desiredReceiptAt", required = false, defaultValue = "") String desiredReceiptAt,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "15") int size
    ) {
        return ResponseEntity.ok(purchaseRequestService.getPurchaseRequests(
                requestNumber,
                title,
                requester,
                department,
                status,
                priority,
                desiredReceiptAt,
                page,
                size
        ));
    }

    @GetMapping("/filter-options")
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_READ)
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        return ResponseEntity.ok(purchaseRequestService.getFilterOptions());
    }

    @GetMapping("/summary")
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_READ)
    public ResponseEntity<PurchaseRequestDto.SummaryResponse> getSummary() {
        return ResponseEntity.ok(purchaseRequestService.getPurchaseRequestSummary());
    }

    @GetMapping("/{requestId}")
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_READ)
    public ResponseEntity<PurchaseRequestDto.DetailResponse> getPurchaseRequestDetail(
            @PathVariable(name = "requestId") Long requestId
    ) {
        return ResponseEntity.ok(purchaseRequestService.getPurchaseRequestDetail(requestId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_WRITE)
    public ResponseEntity<PurchaseRequestDto.DetailResponse> createPurchaseRequest(
            @RequestPart("data") PurchaseRequestDto.CreateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseRequestService.createPurchaseRequest(request, file));
    }

    @PutMapping(
            value = "/{requestId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_WRITE)
    public ResponseEntity<PurchaseRequestDto.DetailResponse> updatePurchaseRequest(
            @PathVariable(name = "requestId") Long requestId,
            @RequestPart("data") PurchaseRequestDto.UpdateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(
                purchaseRequestService.updatePurchaseRequest(requestId, request, file)
        );
    }

    @PatchMapping("/{requestId}/cancel")
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_WRITE)
    public ResponseEntity<PurchaseRequestDto.DetailResponse> cancelPurchaseRequest(
            @PathVariable(name = "requestId") Long requestId
    ) {
        return ResponseEntity.ok(
                purchaseRequestService.cancelPurchaseRequest(requestId)
        );
    }

    @DeleteMapping("/{requestId}")
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_WRITE)
    public ResponseEntity<Void> deletePurchaseRequest(
            @PathVariable(name = "requestId") Long requestId
    ) {
        purchaseRequestService.deletePurchaseRequest(requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_READ)
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable(name = "attachmentId") Long attachmentId,
            Authentication authentication
    ) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "첨부파일을 찾을 수 없습니다. attachmentId=" + attachmentId
                ));
        attachmentAuthorizationService.assertCanDownload(authentication, attachment);

        Path path = Path.of(attachment.getFilePath());

        if (!Files.exists(path)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "첨부파일 실제 파일을 찾을 수 없습니다."
            );
        }

        Resource resource = new PathResource(path);

        String encodedFileName = URLEncoder.encode(
                attachment.getOriginalName(),
                StandardCharsets.UTF_8
        ).replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(encodedFileName, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(resource);
    }

    @GetMapping("/excel")
    @PreAuthorize(SecurityExpressions.PURCHASE_REQUESTS_READ)
    public void exportExcel(HttpServletResponse response) throws IOException {
        Users currentUser = getCurrentUser();
        excelService.exportExcel("purchase-requests", currentUser, response);
    }

    private Users getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Users user) {
            return user;
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
                                        "현재 로그인 사용자를 찾을 수 없습니다."
                                ));
                    } catch (NumberFormatException error) {
                        throw new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "현재 로그인 사용자를 찾을 수 없습니다."
                        );
                    }
                });
    }
}
