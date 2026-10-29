package com.clouddocs.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Azure Storage 접속 정보. 연결 문자열은 반드시 환경변수(AZURE_STORAGE_CONNECTION_STRING)로 주입한다.
 * 값이 없으면 애플리케이션이 기동 시점에 실패(fail-fast)한다.
 */
@Validated
@ConfigurationProperties(prefix = "azure.storage")
public record AzureStorageProperties(
        @NotBlank(message = "AZURE_STORAGE_CONNECTION_STRING 환경변수가 설정되지 않았습니다.") String connectionString,
        @NotBlank String containerName
) {
}
