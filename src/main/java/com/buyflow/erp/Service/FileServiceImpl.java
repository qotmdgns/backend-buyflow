package com.buyflow.erp.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

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

    private final String UPLOAD_DIR = "C:/erp/uploads/";

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
                file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename()
        );
        String extension = FileUploadPolicy.normalizeExtension(originalName);
        FileUploadPolicy.validate(file, extension);

        String savedName = UUID.randomUUID() + extension;
        Path uploadDir = Path.of(UPLOAD_DIR).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        Path targetPath = uploadDir.resolve(savedName).normalize();
        if (!targetPath.startsWith(uploadDir)) {
            throw new IOException("첨부파일 저장 경로가 올바르지 않습니다.");
        }

        file.transferTo(targetPath);
        String filePath = targetPath.toString();

        Users user = null;

        if (userId != null) {
            user = usersRepository.getReferenceById(userId);
        }

        Attachment attachment = Attachment.builder()
                .originalName(originalName)
                .savedName(savedName)
                .filePath(filePath)
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
