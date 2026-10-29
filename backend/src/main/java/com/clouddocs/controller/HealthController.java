package com.clouddocs.controller;

import com.clouddocs.dto.HealthResponse;
import com.clouddocs.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /** DB와 Blob Storage 상태를 함께 점검한다. 하나라도 DOWN이면 503을 반환해 오케스트레이터가 감지할 수 있게 한다. */
    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse health = healthService.check();
        HttpStatus status = "UP".equals(health.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(health);
    }
}
