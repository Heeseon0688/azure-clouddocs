package com.clouddocs.service;

import java.sql.Connection;
import java.time.Instant;

import javax.sql.DataSource;

import com.clouddocs.dto.HealthResponse;
import com.clouddocs.storage.FileStorage;
import com.clouddocs.storage.FileStorage.StorageDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final DataSource dataSource;
    private final FileStorage storage;

    public HealthService(DataSource dataSource, FileStorage storage) {
        this.dataSource = dataSource;
        this.storage = storage;
    }

    public HealthResponse check() {
        boolean dbUp = isDatabaseUp();
        boolean storageUp = storage.isAvailable();
        StorageDescriptor d = storage.describe();

        return new HealthResponse(
                dbUp && storageUp ? UP : DOWN,
                dbUp ? UP : DOWN,
                storageUp ? UP : DOWN,
                new HealthResponse.StorageInfo(d.provider(), d.accountName(), d.containerName(), d.endpoint()),
                Instant.now());
    }

    private boolean isDatabaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }
}
