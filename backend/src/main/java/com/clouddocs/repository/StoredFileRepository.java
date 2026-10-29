package com.clouddocs.repository;

import java.util.List;

import com.clouddocs.domain.StoredFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Page<StoredFile> findByOriginalNameContainingIgnoreCase(String keyword, Pageable pageable);

    List<StoredFile> findTop5ByOrderByUploadedAtDescIdDesc();

    @Query("select coalesce(sum(f.size), 0) from StoredFile f")
    long sumSize();
}
