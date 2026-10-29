package com.clouddocs.dto;

import java.time.Instant;

public record HealthResponse(
        String status,
        String database,
        String storage,
        StorageInfo storageInfo,
        Instant timestamp
) {

    public record StorageInfo(String provider, String accountName, String containerName, String endpoint) {
    }
}
