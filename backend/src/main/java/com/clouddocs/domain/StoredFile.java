package com.clouddocs.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 파일 메타데이터. 실제 바이너리는 Azure Blob Storage에 있고 DB에는 blobName(참조 키)만 저장한다.
 */
@Entity
@Table(name = "stored_files", indexes = {
        @Index(name = "idx_stored_files_uploaded_at", columnList = "uploaded_at"),
        @Index(name = "idx_stored_files_original_name", columnList = "original_name")
})
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "blob_name", nullable = false, unique = true, length = 300)
    private String blobName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false, length = 10)
    private String extension;

    @Column(nullable = false)
    private long size;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected StoredFile() {
    }

    public StoredFile(String originalName, String blobName, String contentType, String extension, long size) {
        this.originalName = originalName;
        this.blobName = blobName;
        this.contentType = contentType;
        this.extension = extension;
        this.size = size;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getBlobName() {
        return blobName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public long getSize() {
        return size;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
