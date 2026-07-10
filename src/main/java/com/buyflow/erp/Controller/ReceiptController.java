package com.buyflow.erp.Controller;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.buyflow.erp.Dto.ReceiptDto;
import com.buyflow.erp.Service.ReceiptService;

import lombok.RequiredArgsConstructor;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import com.buyflow.erp.Entity.Attachment;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Repository.AttachmentRepository;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.AttachmentAuthorizationService;
import com.buyflow.erp.Service.ExcelService;

@RestController
@RequiredArgsConstructor
@RequestMapping({ "/receipts", "/api/receipts" })
public class ReceiptController {

        private final ReceiptService receiptService;
        private final ExcelService excelService;
        private final AttachmentRepository attachmentRepository;
        private final UserRepository userRepository;
        private final AttachmentAuthorizationService attachmentAuthorizationService;

        @GetMapping("/test")
        @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
        public String test() {
                return "receipt ok";
        }

        @GetMapping
        @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
        public ResponseEntity<ReceiptDto.PageResponse<ReceiptDto.ListResponse>> getReceipts(
                        @RequestParam(name = "activeTab", required = false) String activeTab,
                        @RequestParam(name = "cardFilter", required = false) String cardFilter,
                        @RequestParam(name = "orderNumber", required = false) String orderNumber,
                        @RequestParam(name = "supplierKeyword", required = false) String supplierKeyword,
                        @RequestParam(name = "itemKeyword", required = false) String itemKeyword,
                        @RequestParam(name = "warehouseName", required = false) String warehouseName,
                        @RequestParam(name = "expectedFrom", required = false) String expectedFrom,
                        @RequestParam(name = "expectedTo", required = false) String expectedTo,
                        @RequestParam(name = "status", required = false) String status,
                        @RequestParam(name = "page", defaultValue = "1") int page,
                        @RequestParam(name = "size", defaultValue = "10") int size) {

                return ResponseEntity.ok(
                                receiptService.searchReceipts(
                                                activeTab,
                                                cardFilter,
                                                orderNumber,
                                                supplierKeyword,
                                                itemKeyword,
                                                warehouseName,
                                                expectedFrom,
                                                expectedTo,
                                                status,
                                                page,
                                                size));
        }

        @GetMapping("/filter-options")
        @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
        public ResponseEntity<ReceiptDto.FilterOptionsResponse> getFilterOptions() {
                return ResponseEntity.ok(
                                receiptService.getFilterOptions());
        }

        @GetMapping("/form-options")
        @PreAuthorize(SecurityExpressions.RECEIPTS_WRITE)
        public ResponseEntity<ReceiptDto.FormOptionsResponse> getFormOptions() {
                return ResponseEntity.ok(
                                receiptService.getFormOptions());
        }

        @GetMapping("/summary")
        @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
        public ResponseEntity<ReceiptDto.SummaryResponse> getSummary() {
                return ResponseEntity.ok(
                                receiptService.getSummary());
        }

        @GetMapping("/{receiptId:\\d+}")
        @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
        public ResponseEntity<ReceiptDto.DetailResponse> getReceipt(
                        @PathVariable(name = "receiptId") Long receiptId) {

                return ResponseEntity.ok(
                                receiptService.getReceipt(receiptId));
        }

        @GetMapping("/order/{orderId}")
        @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
        public ResponseEntity<ReceiptDto.DetailResponse> getReceiptByOrderId(
                        @PathVariable(name = "orderId") Long orderId) {

                return ResponseEntity.ok(
                                receiptService.getReceiptByOrderId(orderId));
        }

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize(SecurityExpressions.RECEIPTS_WRITE)
        public ResponseEntity<Map<String, Object>> saveReceipt(
                        @RequestPart("data") ReceiptDto.ReceiptCreateRequest request,
                        @RequestPart(value = "file", required = false) MultipartFile file) {

                try {

                        Long receiptId = receiptService.saveReceipt(request, file);

                        return ResponseEntity.ok(
                                        Map.of(
                                                        "success", true,
                                                        "data", Map.of("receiptId", receiptId),
                                                        "message", "저장 완료"));

                } catch (Exception e) {

                        e.printStackTrace();

                        return ResponseEntity.internalServerError()
                                        .body(
                                                        Map.of(
                                                                        "success", false,
                                                                        "message", e.getMessage()));
                }
        }

        @PostMapping("/test")
        @PreAuthorize(SecurityExpressions.RECEIPTS_WRITE)
        public ResponseEntity<String> test(
                        @RequestBody ReceiptDto.ReceiptCreateRequest request) {

                return ResponseEntity.ok("OK");
        }

        @GetMapping("/attachments/download/{attachmentId}")
        @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
        public ResponseEntity<Resource> downloadAttachment(
                        @PathVariable(name = "attachmentId") Long attachmentId,
                        Authentication authentication) {

                Attachment attachment = attachmentRepository.findById(attachmentId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "첨부파일을 찾을 수 없습니다. attachmentId=" + attachmentId));
                attachmentAuthorizationService.assertCanDownload(authentication, attachment);

                Path path = Path.of(attachment.getFilePath());

                if (!Files.exists(path)) {
                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "첨부파일 실제 파일을 찾을 수 없습니다.");
                }

                Resource resource = new PathResource(path);
                String encodedFileName = URLEncoder.encode(
                                attachment.getOriginalName(),
                                StandardCharsets.UTF_8)
                                .replaceAll("\\+", "%20");

                return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .header(
                                                HttpHeaders.CONTENT_DISPOSITION,
                                                ContentDisposition.attachment()
                                                                .filename(encodedFileName, StandardCharsets.UTF_8)
                                                                .build()
                                                                .toString())
                                .body(resource);
        }

        @GetMapping("/excel")
        @PreAuthorize(SecurityExpressions.RECEIPTS_READ)
        public void exportExcel(HttpServletResponse response, Authentication authentication) throws IOException {
                excelService.exportExcel(
                                "receipts",
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
