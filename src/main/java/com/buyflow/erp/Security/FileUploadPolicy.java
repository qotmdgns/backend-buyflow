package com.buyflow.erp.Security;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileUploadPolicy {

    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            ".pdf", Set.of("application/pdf"),
            ".xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/zip",
                    "application/octet-stream"
            ),
            ".xls", Set.of("application/vnd.ms-excel", "application/octet-stream"),
            ".docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip",
                    "application/octet-stream"
            ),
            ".doc", Set.of("application/msword", "application/vnd.ms-word", "application/octet-stream"),
            ".png", Set.of("image/png"),
            ".jpg", Set.of("image/jpeg"),
            ".jpeg", Set.of("image/jpeg")
    );

    private FileUploadPolicy() {
    }

    public static String normalizeExtension(String originalName) {
        if (originalName == null) {
            return "";
        }

        int index = originalName.lastIndexOf('.');
        if (index < 0 || index == originalName.length() - 1) {
            return "";
        }

        return originalName.substring(index).toLowerCase(Locale.ROOT);
    }

    public static void validate(MultipartFile file, String extension) throws IOException {
        if (file == null || file.isEmpty()) {
            throw badRequest("첨부파일이 비어 있습니다.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw badRequest("첨부파일은 20MB 이하만 업로드할 수 있습니다.");
        }

        if (!ALLOWED_CONTENT_TYPES.containsKey(extension)) {
            throw badRequest("허용되지 않은 첨부파일 형식입니다.");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!contentType.isBlank() && !ALLOWED_CONTENT_TYPES.get(extension).contains(contentType)) {
            throw badRequest("첨부파일 MIME 형식이 확장자와 일치하지 않습니다.");
        }

        if (!matchesSignature(file, extension)) {
            throw badRequest("첨부파일 내용이 확장자와 일치하지 않습니다.");
        }
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean matchesSignature(MultipartFile file, String extension) throws IOException {
        byte[] header = readHeader(file, 8);

        return switch (extension) {
            case ".pdf" -> startsWith(header, "%PDF-".getBytes());
            case ".png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            case ".jpg", ".jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case ".xlsx", ".docx" -> startsWith(header, new byte[]{0x50, 0x4B});
            case ".xls", ".doc" -> startsWith(header, new byte[]{
                    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
            });
            default -> false;
        };
    }

    private static byte[] readHeader(MultipartFile file, int size) throws IOException {
        byte[] header = new byte[size];
        int read;

        try (InputStream inputStream = file.getInputStream()) {
            read = inputStream.read(header);
        }

        if (read < 0) {
            return new byte[0];
        }

        if (read == size) {
            return header;
        }

        byte[] result = new byte[read];
        System.arraycopy(header, 0, result, 0, read);
        return result;
    }

    private static boolean startsWith(byte[] actual, byte[] expected) {
        if (actual.length < expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }

        return true;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
