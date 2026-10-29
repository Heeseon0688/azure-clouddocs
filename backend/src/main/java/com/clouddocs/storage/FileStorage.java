package com.clouddocs.storage;

import java.io.InputStream;

/**
 * 오브젝트 스토리지 추상화. 서비스 계층은 Azure SDK에 직접 의존하지 않으므로
 * 단위 테스트에서 Mock으로 교체할 수 있고, 스토리지 구현을 바꿔도 비즈니스 로직은 그대로다.
 */
public interface FileStorage {

    void upload(String blobName, InputStream data, long size, String contentType);

    /** Blob을 스트림으로 연다. Blob이 없으면 {@link com.clouddocs.exception.FileNotFoundException}. */
    InputStream openStream(String blobName);

    /** @return 실제로 삭제했으면 true, 이미 없었으면 false */
    boolean delete(String blobName);

    boolean isAvailable();

    StorageDescriptor describe();

    record StorageDescriptor(String provider, String accountName, String containerName, String endpoint) {
    }
}
