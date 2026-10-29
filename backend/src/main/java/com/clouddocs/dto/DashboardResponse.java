package com.clouddocs.dto;

import java.util.List;

public record DashboardResponse(
        long totalFiles,
        long totalSize,
        List<FileResponse> recentFiles
) {
}
