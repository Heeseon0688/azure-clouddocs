package com.clouddocs.dto;

import java.io.InputStream;

/** 서비스 계층에서 컨트롤러로 다운로드 스트림과 메타데이터를 함께 전달한다. */
public record FileDownload(
        String originalName,
        String contentType,
        long size,
        InputStream content
) {
}
