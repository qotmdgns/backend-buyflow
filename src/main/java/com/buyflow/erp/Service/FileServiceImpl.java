package com.buyflow.erp.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.buyflow.erp.Entity.Attachment;
import com.buyflow.erp.Entity.Users;
import com.buyflow.erp.Repository.AttachmentRepository;
import com.buyflow.erp.Repository.UserRepository;
import com.buyflow.erp.Security.FileUploadPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final AttachmentRepository attachmentRepository;
    private final UserRepository usersRepository;

    @Value("${app.upload-dir:/app/uploads}")
    private String uploadDir;

    @Override
    public Attachment uploadFile(
            MultipartFile file,
            Long userId,
            String userName
    ) throws IOException {
        return uploadFile(file, userId, userName, null);
    }

    @Override
    public Attachment uploadFile(
            MultipartFile file,
            Long userId,
            String userName,
            Long requestId
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null
                        ? "attachment"
                        : file.getOriginalFilename()
        );

        String extension =
                FileUploadPolicy.normalizeExtension(originalName);

        FileUploadPolicy.validate(file, extension);

        String savedName = UUID.randomUUID() + extension;

        Path uploadPath = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(uploadPath);

        Path targetPath = uploadPath
                .resolve(savedName)
                .normalize();

        if (!targetPath.startsWith(uploadPath)) {
            throw new IOException(
                    "첨부파일 저장 경로가 올바르지 않습니다."
            );
        }

        file.transferTo(targetPath);

        Users user = null;

        if (userId != null) {
            user = usersRepository.getReferenceById(userId);
        }

        Attachment attachment = Attachment.builder()
                .originalName(originalName)
                .savedName(savedName)
                .filePath(targetPath.toString())
                .fileSize(file.getSize())
                .extension(extension)
                .uploadedBy(userName)
                .uploadedAt(LocalDateTime.now())
                .requestId(requestId)
                .user(user)
                .build();

        return attachmentRepository.save(attachment);
    }
}