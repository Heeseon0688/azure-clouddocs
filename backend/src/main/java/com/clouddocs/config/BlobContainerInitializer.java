package com.clouddocs.config;

import com.clouddocs.storage.AzureBlobFileStorage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 기동 시 Blob 컨테이너를 보장한다. 스토리지에 접속할 수 없으면 기동을 실패시켜(fail-fast) 문제를 즉시 드러낸다. */
@Component
public class BlobContainerInitializer implements ApplicationRunner {

    private final AzureBlobFileStorage storage;

    public BlobContainerInitializer(AzureBlobFileStorage storage) {
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        storage.ensureContainerExists();
    }
}
