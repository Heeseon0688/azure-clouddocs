package com.clouddocs.config;

import java.time.Duration;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Blob SDK 클라이언트 구성.
 * 연결 문자열만 바꾸면 Azurite(로컬) ↔ 실제 Azure Storage 계정 간 전환이 가능하다.
 */
@Configuration
public class AzureStorageConfig {

    @Bean
    public BlobServiceClient blobServiceClient(AzureStorageProperties properties) {
        // 지수 백오프 재시도: 일시적인 네트워크 오류(429/503 등)에 대한 기본 복원력 확보
        RequestRetryOptions retryOptions = new RequestRetryOptions(
                RetryPolicyType.EXPONENTIAL, 3, Duration.ofSeconds(30),
                Duration.ofMillis(500), Duration.ofSeconds(5), null);

        return new BlobServiceClientBuilder()
                .connectionString(properties.connectionString())
                .retryOptions(retryOptions)
                .buildClient();
    }

    @Bean
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient,
                                                   AzureStorageProperties properties) {
        return blobServiceClient.getBlobContainerClient(properties.containerName());
    }
}
