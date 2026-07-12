package com.buyflow.erp.Controller;

import java.io.IOException;
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

import com.buyflow.erp.Common.BusinessException;
import com.buyflow.erp.Common.ErrorCode;
import com.buyflow.erp.Entity.Attachment;
import com.buyflow.erp.Security.SecurityExpressions;
import com.buyflow.erp.Service.AttachmentAuthorizationService;
import com.buyflow.erp.Service.AttachmentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AttachmentAuthorizationService attachmentAuthorizationService;

    @GetMapping("/attachments/download/{attachmentId}")
    @PreAuthorize(SecurityExpressions.AUTHENTICATED)
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("attachmentId") Long attachmentId,
            Authentication authentication) throws IOException {
        Attachment attachment = attachmentService.getAttachmentInfo(attachmentId);
        attachmentAuthorizationService.assertCanDownload(authentication, attachment);

        Path filePath = Paths.get(attachment.getFilePath());
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "첨부파일을 찾을 수 없습니다.");
        }

        Resource resource = new InputStreamResource(Files.newInputStream(filePath));
        String encodedFileName = UriUtils.encode(attachment.getOriginalName(), StandardCharsets.UTF_8);
        String contentDisposition = "attachment; filename=\"" + encodedFileName + "\"";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(filePath))
                .body(resource);
    }
}
