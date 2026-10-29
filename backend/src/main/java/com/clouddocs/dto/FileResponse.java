package com.clouddocs.dto;

import java.time.Instant;

import com.clouddocs.domain.StoredFile;

public record FileResponse(
        Long id,
        String originalName,
        String contentType,
        String extension,
        long size,
        Instant uploadedAt,
        String downloadUrl
) {

    public static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getExtension(),
                file.getSize(),
                file.getUploadedAt(),
                "/api/files/" + file.getId() + "/download");
    }
}
