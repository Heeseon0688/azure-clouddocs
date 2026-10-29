package com.clouddocs.controller;

import com.clouddocs.dto.DashboardResponse;
import com.clouddocs.service.FileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final FileService fileService;

    public DashboardController(FileService fileService) {
        this.fileService = fileService;
    }

    /** 전체 파일 수, 전체 저장 용량, 최근 업로드 5건 */
    @GetMapping("/summary")
    public DashboardResponse summary() {
        return fileService.dashboard();
    }
}
