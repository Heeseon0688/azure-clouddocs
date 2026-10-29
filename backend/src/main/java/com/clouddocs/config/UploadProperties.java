package com.clouddocs.config;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "clouddocs.upload")
public record UploadProperties(DataSize maxFileSize, Set<String> allowedExtensions) {

    public UploadProperties {
        allowedExtensions = allowedExtensions.stream()
                .map(ext -> ext.trim().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
    }
}
