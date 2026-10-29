package com.clouddocs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.clouddocs.config.UploadProperties;
import com.clouddocs.exception.FileTooLargeException;
import com.clouddocs.exception.InvalidFileException;
import com.clouddocs.service.FileValidator.ValidatedFile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class FileValidatorTest {

    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

    private final FileValidator validator = new FileValidator(
            new UploadProperties(DataSize.ofMegabytes(10), Set.of("pdf", "png", "jpg", "jpeg", "txt")));

    @Test
    void acceptsTextFileAndDecidesContentTypeOnServer() {
        var file = new MockMultipartFile("file", "보고서.TXT", "application/octet-stream",
                "hello".getBytes(StandardCharsets.UTF_8));

        ValidatedFile result = validator.validate(file);

        assertThat(result.originalName()).isEqualTo("보고서.TXT");
        assertThat(result.extension()).isEqualTo("txt");
        assertThat(result.contentType()).startsWith("text/plain");
    }

    @Test
    void acceptsPngWithValidSignature() {
        var file = new MockMultipartFile("file", "logo.png", "image/png", PNG_HEADER);

        assertThat(validator.validate(file).contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsUnsupportedExtension() {
        var file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("지원하지 않는 파일 형식");
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchExtension() {
        var file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("일치하지 않습니다");
    }

    @Test
    void rejectsFileLargerThanLimit() {
        var file = new MockMultipartFile("file", "big.txt", "text/plain", new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(InvalidFileException.class);
    }

    @Test
    void stripsPathTraversalFromFileName() {
        assertThat(FileValidator.sanitizeFileName("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(FileValidator.sanitizeFileName("C:\\Users\\me\\doc.pdf")).isEqualTo("doc.pdf");
    }
}
