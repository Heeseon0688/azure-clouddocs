package com.clouddocs.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.clouddocs.domain.StoredFile;
import com.clouddocs.dto.DashboardResponse;
import com.clouddocs.dto.FileDownload;
import com.clouddocs.dto.FileResponse;
import com.clouddocs.dto.PageResponse;
import com.clouddocs.exception.FileNotFoundException;
import com.clouddocs.exception.StorageException;
import com.clouddocs.repository.StoredFileRepository;
import com.clouddocs.service.FileValidator.ValidatedFile;
import com.clouddocs.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final DateTimeFormatter BLOB_DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));

    private final StoredFileRepository repository;
    private final FileStorage storage;
    private final FileValidator validator;

    public FileService(StoredFileRepository repository, FileStorage storage, FileValidator validator) {
        this.repository = repository;
        this.storage = storage;
        this.validator = validator;
    }

    /**
     * 업로드 순서: (1) 검증 → (2) Blob 업로드 → (3) 메타데이터 저장.
     * DB와 Blob은 하나의 트랜잭션으로 묶을 수 없으므로, (3)이 실패하면 (2)를 삭제하는 보상 처리로 고아 Blob을 막는다.
     */
    public FileResponse upload(MultipartFile file) {
        ValidatedFile validated = validator.validate(file);
        String blobName = generateBlobName(validated.extension());

        try (InputStream in = file.getInputStream()) {
            storage.upload(blobName, in, validated.size(), validated.contentType());
        } catch (IOException e) {
            throw new StorageException("업로드 파일을 읽는 중 오류가 발생했습니다.", e);
        }

        try {
            StoredFile saved = repository.save(new StoredFile(
                    validated.originalName(), blobName, validated.contentType(),
                    validated.extension(), validated.size()));
            return FileResponse.from(saved);
        } catch (RuntimeException e) {
            log.warn("Metadata save failed. Rolling back blob '{}'", blobName);
            safeDeleteBlob(blobName);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> list(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, LATEST_FIRST);
        var result = StringUtils.hasText(keyword)
                ? repository.findByOriginalNameContainingIgnoreCase(keyword.trim(), pageable)
                : repository.findAll(pageable);
        return PageResponse.of(result, FileResponse::from);
    }

    @Transactional(readOnly = true)
    public FileResponse get(Long id) {
        return FileResponse.from(findById(id));
    }

    @Transactional(readOnly = true)
    public FileDownload download(Long id) {
        StoredFile file = findById(id);
        InputStream content = storage.openStream(file.getBlobName());
        return new FileDownload(file.getOriginalName(), file.getContentType(), file.getSize(), content);
    }

    /**
     * 메타데이터 삭제(flush) 후 Blob을 삭제한다. Blob 삭제가 실패하면 예외로 트랜잭션이 롤백되어
     * 메타데이터가 복구되므로, 사용자는 목록에서 파일을 계속 보고 재시도할 수 있다.
     */
    @Transactional
    public void delete(Long id) {
        StoredFile file = findById(id);
        repository.delete(file);
        repository.flush();
        storage.delete(file.getBlobName());
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        return new DashboardResponse(
                repository.count(),
                repository.sumSize(),
                repository.findTop5ByOrderByUploadedAtDescIdDesc().stream().map(FileResponse::from).toList());
    }

    private StoredFile findById(Long id) {
        return repository.findById(id).orElseThrow(() -> FileNotFoundException.ofId(id));
    }

    /** 원본 파일명은 DB에만 저장하고, Blob 이름은 충돌/인코딩 문제가 없는 UUID 기반으로 만든다. */
    private static String generateBlobName(String extension) {
        String datePath = LocalDate.now(ZoneOffset.UTC).format(BLOB_DATE_PATH);
        return "uploads/%s/%s.%s".formatted(datePath, UUID.randomUUID(), extension);
    }

    private void safeDeleteBlob(String blobName) {
        try {
            storage.delete(blobName);
        } catch (RuntimeException e) {
            log.error("Compensation failed. Orphan blob remains: {}", blobName, e);
        }
    }
}
