package com.clouddocs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.azure.storage.blob.BlobContainerClient;
import com.clouddocs.domain.StoredFile;
import com.clouddocs.exception.StorageException;
import com.clouddocs.repository.StoredFileRepository;
import com.clouddocs.storage.FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 통합 테스트: Spring MVC → Service → JPA(H2) → Azure Blob SDK → Azurite(실제 에뮬레이터).
 * Blob Storage는 Mock이 아니라 실제 Azurite에 요청을 보낸다.
 * 실행 전 Azurite가 필요하다 (README의 "테스트 실행" 참고).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoredFileRepository repository;

    @Autowired
    private BlobContainerClient container;

    @MockitoSpyBean
    private FileStorage storage;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @AfterEach
    void cleanContainer() {
        container.listBlobs().forEach(blob -> container.getBlobClient(blob.getName()).deleteIfExists());
    }

    @Test
    void healthReportsDatabaseAndAzuriteUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.storage").value("UP"))
                .andExpect(jsonPath("$.storageInfo.provider").value("Azurite (Local Emulator)"));
    }

    @Test
    void uploadListDownloadDeleteLifecycle() throws Exception {
        byte[] body = "Azure CloudDocs 통합 테스트".getBytes(StandardCharsets.UTF_8);

        // 1) 업로드 → 201 Created + Location
        JsonNode created = upload(new MockMultipartFile("file", "회의록.txt", "text/plain", body));
        long id = created.get("id").asLong();
        assertThat(created.get("originalName").asText()).isEqualTo("회의록.txt");
        assertThat(created.get("size").asLong()).isEqualTo(body.length);

        // 2) Blob이 Azurite에 실제로 저장되었는지 SDK로 직접 확인
        StoredFile saved = repository.findById(id).orElseThrow();
        assertThat(container.getBlobClient(saved.getBlobName()).exists()).isTrue();

        // 3) 목록 + 대시보드
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(jsonPath("$.totalFiles").value(1))
                .andExpect(jsonPath("$.totalSize").value(body.length));

        // 4) 다운로드: 원본 내용과 한글 파일명(RFC 5987) 유지
        mockMvc.perform(get("/api/files/{id}/download", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                .andExpect(header().string("Content-Disposition", containsString("%ED%9A%8C%EC%9D%98%EB%A1%9D.txt")))
                .andExpect(content().bytes(body));

        // 5) 삭제 → 204, Blob과 메타데이터 모두 제거
        mockMvc.perform(delete("/api/files/{id}", id)).andExpect(status().isNoContent());
        assertThat(repository.findById(id)).isEmpty();
        assertThat(container.getBlobClient(saved.getBlobName()).exists()).isFalse();
        mockMvc.perform(get("/api/files/{id}/download", id)).andExpect(status().isNotFound());
    }

    @Test
    void searchesByFileNameAndSortsLatestFirst() throws Exception {
        upload(textFile("2024_annual_report.txt"));
        upload(textFile("meeting-notes.txt"));
        upload(textFile("2025_annual_REPORT.txt"));

        mockMvc.perform(get("/api/files").param("keyword", "report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].originalName").value("2025_annual_REPORT.txt"))
                .andExpect(jsonPath("$.content[1].originalName").value("2024_annual_report.txt"));
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "setup.exe", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("지원하지 않는 파일 형식")));
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsSpoofedImage() throws Exception {
        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", "plain text".getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFileOverTenMegabytes() throws Exception {
        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "big.txt", "text/plain", new byte[10 * 1024 * 1024 + 1])))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void keepsMetadataWhenBlobDeletionFails() throws Exception {
        long id = upload(textFile("keep-me.txt")).get("id").asLong();
        doThrow(new StorageException("simulated failure", null)).when(storage).delete(anyString());

        mockMvc.perform(delete("/api/files/{id}", id))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Storage error"));

        // 트랜잭션 롤백으로 메타데이터가 남아 있어야 재시도할 수 있다
        assertThat(repository.findById(id)).isPresent();
    }

    @Test
    void returns404WhenBlobIsMissingInStorage() throws Exception {
        long id = upload(textFile("ghost.txt")).get("id").asLong();
        container.getBlobClient(repository.findById(id).orElseThrow().getBlobName()).delete();

        mockMvc.perform(get("/api/files/{id}/download", id)).andExpect(status().isNotFound());
    }

    @Test
    void returns404ForUnknownFile() throws Exception {
        mockMvc.perform(delete("/api/files/{id}", 999_999)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files/{id}", 999_999)).andExpect(status().isNotFound());
    }

    private JsonNode upload(MockMultipartFile file) throws Exception {
        String json = mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/files/")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private static MockMultipartFile textFile(String name) {
        return new MockMultipartFile("file", name, "text/plain", ("content of " + name).getBytes());
    }
}
