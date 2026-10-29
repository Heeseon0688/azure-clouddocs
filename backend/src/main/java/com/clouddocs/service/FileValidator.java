package com.clouddocs.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

import com.clouddocs.config.UploadProperties;
import com.clouddocs.exception.FileTooLargeException;
import com.clouddocs.exception.InvalidFileException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일 검증: 빈 파일 / 크기 / 확장자 화이트리스트 / 매직 넘버(파일 시그니처).
 * Content-Type은 클라이언트가 조작할 수 있으므로 신뢰하지 않고, 서버가 확장자 기준으로 결정한다.
 */
@Component
public class FileValidator {

    private static final int MAX_FILENAME_LENGTH = 255;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "txt", "text/plain; charset=utf-8");

    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final UploadProperties properties;

    public FileValidator(UploadProperties properties) {
        this.properties = properties;
    }

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("업로드할 파일이 비어 있습니다.");
        }
        long maxBytes = properties.maxFileSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new FileTooLargeException("파일 크기는 최대 %dMB까지 업로드할 수 있습니다."
                    .formatted(properties.maxFileSize().toMegabytes()));
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extractExtension(fileName);
        if (!properties.allowedExtensions().contains(extension) || !CONTENT_TYPES.containsKey(extension)) {
            throw new InvalidFileException("지원하지 않는 파일 형식입니다. (허용: PDF, PNG, JPG, TXT)");
        }
        verifySignature(file, extension);

        return new ValidatedFile(fileName, extension, CONTENT_TYPES.get(extension), file.getSize());
    }

    /** 경로 조작(../, C:\) 방지를 위해 디렉터리 부분을 제거하고 제어 문자를 없앤다. */
    static String sanitizeFileName(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            throw new InvalidFileException("파일 이름이 없습니다.");
        }
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty() || name.startsWith(".")) {
            throw new InvalidFileException("올바르지 않은 파일 이름입니다.");
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidFileException("파일 이름은 %d자 이하여야 합니다.".formatted(MAX_FILENAME_LENGTH));
        }
        return name;
    }

    static String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new InvalidFileException("파일 확장자가 없습니다. (허용: PDF, PNG, JPG, TXT)");
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private static void verifySignature(MultipartFile file, String extension) {
        byte[] expected = switch (extension) {
            case "pdf" -> PDF_SIGNATURE;
            case "png" -> PNG_SIGNATURE;
            case "jpg", "jpeg" -> JPEG_SIGNATURE;
            default -> null; // txt는 고정 시그니처가 없다
        };
        if (expected == null) {
            return;
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(expected.length);
            if (!Arrays.equals(header, expected)) {
                throw new InvalidFileException("파일 내용이 확장자(." + extension + ")와 일치하지 않습니다.");
            }
        } catch (IOException e) {
            throw new InvalidFileException("업로드 파일을 읽을 수 없습니다.");
        }
    }

    public record ValidatedFile(String originalName, String extension, String contentType, long size) {
    }
}
