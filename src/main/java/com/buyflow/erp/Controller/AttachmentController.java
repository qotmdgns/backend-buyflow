package com.buyflow.erp.Controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import com.buyflow.erp.Entity.Attachment;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.AttachmentAuthorizationService;
import com.buyflow.erp.Service.AttachmentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService; // 🚀 방금 만든 서비스 주입!
    private final AttachmentAuthorizationService attachmentAuthorizationService;

    @GetMapping("/api/attachments/download/{attachmentId}")
    @PreAuthorize(SecurityExpressions.AUTHENTICATED)
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("attachmentId") Long attachmentId,
            Authentication authentication) {
        try {
            // 1. 서비스에서 파일 정보 가져오기
            Attachment attachment = attachmentService.getAttachmentInfo(attachmentId); 
            attachmentAuthorizationService.assertCanDownload(authentication, attachment);

            // 2. 실제 파일 경로 설정
            Path filePath = Paths.get(attachment.getFilePath()); 

            if (!Files.exists(filePath)) {
                throw new RuntimeException("서버에 실제 파일이 존재하지 않습니다.");
            }

            Resource resource = new InputStreamResource(Files.newInputStream(filePath));

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
