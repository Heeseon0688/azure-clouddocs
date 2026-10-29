package com.clouddocs.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.clouddocs.dto.FileDownload;
import com.clouddocs.dto.FileResponse;
import com.clouddocs.dto.PageResponse;
import com.clouddocs.service.FileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /** 파일 목록 (최신 업로드 순). keyword가 있으면 파일명 부분 일치 검색. */
    @GetMapping
    public PageResponse<FileResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return fileService.list(keyword, page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(@RequestPart("file") MultipartFile file) {
        FileResponse created = fileService.upload(file);
        return ResponseEntity.created(URI.create("/api/files/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public FileResponse get(@PathVariable Long id) {
        return fileService.get(id);
    }

    /** Blob을 메모리에 모두 올리지 않고 스트리밍으로 전달한다. 한글 파일명은 RFC 5987(filename*)로 인코딩. */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        FileDownload download = fileService.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
