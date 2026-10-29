package com.clouddocs.storage;

import java.io.InputStream;
import java.time.Duration;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.clouddocs.exception.FileNotFoundException;
import com.clouddocs.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Azure Storage Blob SDK for Java 기반 구현체.
 * Azurite와 실제 Azure Storage는 동일한 REST API를 제공하므로 코드 변경 없이 연결 문자열만으로 전환된다.
 */
@Component
public class AzureBlobFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobFileStorage.class);
    private static final String EMULATOR_ACCOUNT = "devstoreaccount1";
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(3);

    private final BlobContainerClient container;

    public AzureBlobFileStorage(BlobContainerClient container) {
        this.container = container;
    }

    /** 애플리케이션 기동 시 컨테이너가 없으면 생성한다. */
    public void ensureContainerExists() {
        boolean created = container.createIfNotExists();
        log.info("Blob container '{}' {} ({})", container.getBlobContainerName(),
                created ? "created" : "already exists", describe().provider());
    }

    @Override
    public void upload(String blobName, InputStream data, long size, String contentType) {
        BlobClient blob = container.getBlobClient(blobName);
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(data)
                .setHeaders(new BlobHttpHeaders().setContentType(contentType))
                // If-None-Match: * → 같은 이름의 Blob이 있으면 덮어쓰지 않고 실패
                .setRequestConditions(new BlobRequestConditions().setIfNoneMatch("*"));
        try {
            blob.uploadWithResponse(options, null, Context.NONE);
            log.info("Uploaded blob '{}' ({} bytes)", blobName, size);
        } catch (RuntimeException e) {
            throw new StorageException("Blob Storage 업로드에 실패했습니다.", e);
        }
    }

    @Override
    public InputStream openStream(String blobName) {
        try {
            return container.getBlobClient(blobName).openInputStream();
        } catch (BlobStorageException e) {
            if (e.getErrorCode() == BlobErrorCode.BLOB_NOT_FOUND || e.getStatusCode() == 404) {
                throw new FileNotFoundException("스토리지에서 파일을 찾을 수 없습니다.");
            }
            throw new StorageException("Blob Storage 다운로드에 실패했습니다.", e);
        } catch (RuntimeException e) {
            throw new StorageException("Blob Storage 다운로드에 실패했습니다.", e);
        }
    }

    @Override
    public boolean delete(String blobName) {
        try {
            boolean deleted = container.getBlobClient(blobName)
                    .deleteIfExistsWithResponse(DeleteSnapshotsOptionType.INCLUDE, null, null, Context.NONE)
                    .getValue();
            log.info("Deleted blob '{}' (existed={})", blobName, deleted);
            return deleted;
        } catch (RuntimeException e) {
            throw new StorageException("Blob Storage 파일 삭제에 실패했습니다.", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return container.existsWithResponse(HEALTH_TIMEOUT, Context.NONE).getValue();
        } catch (RuntimeException e) {
            log.warn("Blob Storage health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public StorageDescriptor describe() {
        String account = container.getAccountName();
        String provider = EMULATOR_ACCOUNT.equals(account) ? "Azurite (Local Emulator)" : "Azure Blob Storage";
        return new StorageDescriptor(provider, account, container.getBlobContainerName(),
                container.getBlobContainerUrl());
    }
}
