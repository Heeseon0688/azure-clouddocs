# Azure CloudDocs

**Azure Blob Storage API**를 이용하는 기업용 문서 관리 웹 서비스입니다.
파일 바이너리는 Azure Blob Storage에, 메타데이터는 MySQL에 저장하며 Spring Boot REST API와 React 관리자 대시보드로 구성됩니다.
Azure 계정 없이도 공식 에뮬레이터 **Azurite**로 전체 기능이 동작하고, **연결 문자열만 교체하면 실제 Azure Storage**에서 그대로 동작합니다.

| 서비스 | 주소 |
|---|---|
| 웹 대시보드 (React + nginx) | http://localhost:3100 |
| Backend API (Spring Boot) | http://localhost:8081/api/health |
| Azurite Blob endpoint | http://localhost:10000/devstoreaccount1 |
| MySQL | localhost:3306 (db: `clouddocs`) |

> 호스트 포트는 `.env`의 `FRONTEND_PORT`, `BACKEND_PORT` 등으로 바꿀 수 있습니다.

---

## 1. 시스템 아키텍처

```
                     ┌──────────────────────── docker compose (network: azure-clouddocs_default) ────────────────────────┐
                     │                                                                                                    │
 Browser ──:3100──▶  │  frontend (nginx)                     backend (Spring Boot 3.5 / Java 21)                          │
                     │  ├─ React SPA 정적 파일                ┌──────────────────────────────────────────────┐             │
                     │  └─ /api/* ──proxy──▶ backend:8080 ──▶ │ Controller  (REST, DTO, Problem Details)     │             │
                     │                                        │     │                                        │             │
                     │                                        │ Service     (검증·보상 트랜잭션·정합성)        │             │
                     │                                        │     │                  │                     │             │
                     │                                        │ Repository (JPA)   FileStorage (interface)   │             │
                     │                                        │     │              AzureBlobFileStorage       │             │
                     │                                        └─────┼──────────────────┼────────────────────┘             │
                     │                                              │ JDBC             │ Azure Storage Blob SDK (HTTP REST) │
                     │                                              ▼                  ▼                                    │
                     │                                        mysql:3306          azurite:10000  ◀── 연결 문자열만 교체 ──▶ │ Azure Storage Account
                     │                                        (메타데이터)        (Blob 바이너리)                          │ (*.blob.core.windows.net)
                     └────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

- **브라우저는 nginx 하나만 바라봅니다.** nginx가 `/api`를 Backend로 프록시하므로 운영 환경에서도 CORS 없이 같은 origin으로 동작합니다.
  (Vite 개발 서버 `:5173`에서 Backend로 직접 호출하는 경우를 위해 CORS 화이트리스트도 설정되어 있습니다.)
- **Blob 키 ↔ 메타데이터 분리**: DB에는 원본 파일명·크기·타입과 `blob_name`(참조 키)만 저장하고, 실제 바이트는 Blob Storage에만 존재합니다.
- **스토리지 추상화**: 서비스 계층은 `FileStorage` 인터페이스에만 의존합니다. Azure SDK 의존은 `AzureBlobFileStorage` 한 클래스에 격리되어 있습니다.

### 패키지 구조 (backend)

```
com.clouddocs
├── controller   FileController, DashboardController, HealthController
├── service      FileService(업로드/조회/삭제/대시보드), FileValidator, HealthService
├── storage      FileStorage(인터페이스), AzureBlobFileStorage(Azure SDK 구현)
├── repository   StoredFileRepository (Spring Data JPA)
├── domain       StoredFile (JPA Entity)
├── dto          FileResponse, PageResponse, DashboardResponse, HealthResponse, FileDownload
├── exception    GlobalExceptionHandler(RFC 9457), 도메인 예외들
└── config       AzureStorageConfig, AzureStorageProperties, UploadProperties, WebConfig(CORS), BlobContainerInitializer
```

---

## 2. 사용 기술과 선택 이유

| 영역 | 기술 | 선택 이유 |
|---|---|---|
| Language | **Java 21** | LTS. `record` DTO, `switch` 식, 텍스트 포매팅 등으로 간결한 코드 |
| Framework | **Spring Boot 3.5** | 내장 Tomcat, Spring Data JPA, Bean Validation, `ProblemDetail`(RFC 9457) 기본 지원 |
| Build | **Maven** (+ Maven Wrapper) | 채용 공고 스택. Azure SDK BOM import로 Azure 라이브러리 버전 일관성 확보 |
| Cloud SDK | **Azure Storage Blob SDK for Java** (`azure-sdk-bom 1.3.9`, blob 12.36) | Microsoft 공식 SDK. 재시도 정책, 병렬 업로드, 스트리밍 다운로드를 기본 제공 |
| Emulator | **Azurite 3.37** | Microsoft 공식 로컬 에뮬레이터. Blob REST API 호환 → 코드 변경 없이 클라우드 전환 |
| DB | **MySQL 8.4** (JPA / Hibernate) | 공고 스택(MySQL/MariaDB). 스키마는 `ddl-auto: update`로 간단 초기화 |
| Frontend | **React 18 + TypeScript + Vite** | 타입 안전한 API 계약(types.ts), 빠른 빌드. UI 라이브러리 없이 순수 CSS로 경량화 |
| Web server | **nginx** | 정적 파일 서빙 + `/api` 리버스 프록시(업·다운로드 스트리밍 설정) |
| Infra | **Docker Compose** | `docker compose up --build` 한 줄로 4개 서비스 기동. healthcheck 기반 기동 순서 제어 |
| Test | JUnit 5, MockMvc, AssertJ, H2, **실제 Azurite** | Blob은 Mock이 아닌 실제 에뮬레이터로 검증, DB는 H2(MySQL 모드)로 빠르게 |

모든 이미지(mysql, azurite, temurin, node, nginx)는 **linux/arm64를 지원하는 멀티 아키텍처 이미지**라 Apple Silicon Mac에서 에뮬레이션 없이 동작합니다.

---

## 3. 주요 API 목록

| Method | URL | 설명 | 성공 응답 |
|---|---|---|---|
| GET | `/api/health` | DB + Blob Storage 상태 점검 (DOWN이면 **503**) | 200 |
| GET | `/api/files?keyword=&page=0&size=20` | 파일 목록 (최신 업로드 순, 파일명 부분일치 검색, 페이지네이션) | 200 |
| POST | `/api/files` (multipart `file`) | 파일 업로드 → Blob 저장 + 메타데이터 저장 | **201** + `Location` |
| GET | `/api/files/{id}` | 파일 메타데이터 단건 조회 | 200 |
| GET | `/api/files/{id}/download` | Blob 스트리밍 다운로드 (원본 파일명 유지) | 200 |
| DELETE | `/api/files/{id}` | Blob + 메타데이터 삭제 | **204** |
| GET | `/api/dashboard/summary` | 전체 파일 수, 전체 용량, 최근 업로드 5건 | 200 |

**RESTful 설계 포인트**: 리소스 명사형 URL(`/files`), HTTP 메서드로 행위 표현, 생성 시 `201 Created + Location`, 삭제 시 `204 No Content`, 하위 리소스로 다운로드 표현(`/files/{id}/download`), 검색·정렬·페이지는 쿼리 파라미터.

### 오류 응답 (RFC 9457 Problem Details, `application/problem+json`)

```json
{ "type": "about:blank", "title": "Invalid file", "status": 400,
  "detail": "지원하지 않는 파일 형식입니다. (허용: PDF, PNG, JPG, TXT)", "instance": "/api/files" }
```

| 상황 | 상태 코드 |
|---|---|
| 확장자 미허용 / 내용과 확장자 불일치(매직 넘버) / 빈 파일 / 잘못된 파라미터 | 400 |
| 존재하지 않는 파일 (DB 또는 Blob에 없음) | 404 |
| 10MB 초과 | 413 |
| Blob Storage 호출 실패 | 502 |
| Health check 실패 | 503 |

### 호출 예시

```bash
curl -F "file=@report.pdf" http://localhost:8081/api/files
curl "http://localhost:8081/api/files?keyword=report"
curl -OJ http://localhost:8081/api/files/1/download
curl -X DELETE http://localhost:8081/api/files/1
```

---

## 4. Azure Blob Storage 연동 흐름

### SDK 클라이언트 계층

```java
BlobServiceClient    service   = new BlobServiceClientBuilder()
                                     .connectionString(env AZURE_STORAGE_CONNECTION_STRING)
                                     .retryOptions(지수 백오프 3회)
                                     .buildClient();          // 스토리지 계정 단위
BlobContainerClient  container = service.getBlobContainerClient("documents"); // 컨테이너 단위 (Bean)
BlobClient           blob      = container.getBlobClient("uploads/2026/10/29/<uuid>.pdf"); // Blob 단위
```

- 기동 시 `BlobContainerInitializer`가 `createIfNotExists()`로 컨테이너를 보장합니다. 스토리지에 접근할 수 없으면 **기동 실패(fail-fast)**.
- 연결 문자열이 비어 있으면 `@Validated` 설정 클래스가 기동 시점에 명확한 메시지로 실패합니다.

### 업로드 (`POST /api/files`)

```
Client ─▶ Controller ─▶ FileValidator: 비어있음 / 10MB / 확장자 화이트리스트 / 매직넘버 검사, Content-Type은 서버가 결정
                     ─▶ blobName = uploads/yyyy/MM/dd/{UUID}.{ext}      (원본 파일명은 DB에만 저장)
                     ─▶ BlobClient.uploadWithResponse(stream, headers(Content-Type), If-None-Match:*)
                     ─▶ JPA save(StoredFile)
                         └─ 실패 시 보상 처리: 방금 올린 Blob 삭제 → 고아 Blob 방지
                     ◀─ 201 Created, Location: /api/files/{id}
```

### 다운로드 (`GET /api/files/{id}/download`)

```
DB에서 메타데이터 조회 (없으면 404)
 ─▶ BlobClient.openInputStream()  (Blob 없으면 404, 스토리지 장애면 502 — 응답 헤더를 쓰기 전에 판단)
 ─▶ InputStreamResource로 청크 단위 스트리밍 (서버 메모리에 파일 전체를 올리지 않음)
 ─▶ Content-Disposition: attachment; filename*=UTF-8''%EB%B6%84...  (RFC 5987, 한글 파일명 유지)
```

### 삭제 (`DELETE /api/files/{id}`)

```
@Transactional
 ├─ repository.delete + flush       (DB 변경을 먼저 실행해 제약 위반 등을 조기에 발견)
 └─ BlobClient.deleteIfExists(스냅샷 포함)
      └─ 실패 시 StorageException → 트랜잭션 롤백 → 메타데이터 유지 → 502 응답 → 사용자가 재시도 가능
```

---

## 5. 실행 방법

### 사전 준비
- Docker Desktop (Apple Silicon / Intel 모두 가능)

### 전체 실행

```bash
docker compose up --build        # 백그라운드 실행은 -d
```

기동 순서는 healthcheck로 보장됩니다.

```
mysql (mysqladmin ping, TCP) ─┐
                              ├─ healthy ─▶ backend (/api/health = 200) ─ healthy ─▶ frontend
azurite (nc -z 10000) ────────┘
```

- MySQL 공식 이미지는 초기화 중 `--skip-networking` 임시 서버를 띄우므로 healthcheck를 **127.0.0.1(TCP)** 로 걸어 "진짜 준비 완료" 시점만 통과시킵니다.
- `.env`가 없어도 compose의 로컬 기본값으로 바로 실행됩니다. 값을 바꾸려면 `cp .env.example .env` 후 수정하세요.

브라우저에서 http://localhost:3100 접속 → 파일 업로드 / 검색 / 다운로드 / 삭제.

```bash
docker compose logs -f backend   # 로그
docker compose down              # 중지
docker compose down -v           # 중지 + MySQL/Azurite 데이터 삭제
```

### 로컬 개발 모드 (선택, Java 21 + Node 20+ 필요)

```bash
docker compose up -d mysql azurite
cd backend && \
  DB_PASSWORD=clouddocs-local \
  AZURE_STORAGE_CONNECTION_STRING='UseDevelopmentStorage=true' \
  SERVER_PORT=8081 ./mvnw spring-boot:run       # 호스트에서는 Azurite가 127.0.0.1:10000
cd frontend && npm install && npm run dev        # http://localhost:5173 (/api → :8081 프록시)
```

### 테스트 실행

통합 테스트는 **실제 Azurite**에 Blob SDK로 요청합니다(DB는 H2 MySQL 모드). 로컬에 Java가 없어도 Docker로 실행할 수 있습니다.

```bash
docker compose up -d azurite
docker run --rm --network azure-clouddocs_default \
  -v "$PWD/backend":/app -v clouddocs-m2:/root/.m2 -w /app \
  -e 'AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://azurite:10000/devstoreaccount1;' \
  maven:3.9-eclipse-temurin-21 mvn -B test

# Java 21이 설치되어 있다면 (기본값 UseDevelopmentStorage=true → 127.0.0.1:10000)
cd backend && ./mvnw test
```

| 테스트 | 종류 | 검증 내용 |
|---|---|---|
| `FileValidatorTest` (7) | 단위 | 확장자 화이트리스트, 매직넘버 위조 차단, 10MB 초과, 빈 파일, 경로 조작(`../`, `C:\`) 제거 |
| `FileApiIntegrationTest` (9) | 통합 (MockMvc + H2 + **Azurite**) | 업로드→Azurite에 Blob 존재 확인→목록→대시보드→다운로드(바이트·한글 파일명)→삭제→Blob 제거 확인, 검색+정렬, 400/404/413, **Blob 삭제 장애 주입 시 메타데이터 롤백**, Blob 유실 시 404, Health |

---

## 6. 면접에서 설명할 핵심 기술 5가지

### ① Azure Blob SDK 연동과 "연결 문자열 하나로" 환경 전환
`BlobServiceClient → BlobContainerClient → BlobClient` 계층을 Bean으로 구성하고, 연결 문자열은 환경변수로만 주입합니다.
Azurite는 Blob REST API를 그대로 구현하므로 **코드 수정 없이** `AZURE_STORAGE_CONNECTION_STRING`만 바꾸면 실제 Azure로 전환됩니다.
컨테이너 내부에서는 `localhost`가 아니라 Compose 서비스명 `azurite`로 접속해야 한다는 점(Docker DNS)도 반영했습니다.
`/api/health`가 현재 연결된 스토리지(Azurite / Azure)와 컨테이너를 보여줘 데모에서 바로 확인할 수 있습니다.

### ② DB와 Blob Storage 사이의 데이터 정합성 (분산 트랜잭션 없이)
DB와 Blob은 하나의 트랜잭션으로 묶을 수 없습니다.
- **업로드**: Blob 먼저 → DB 저장. DB가 실패하면 **보상 처리**로 Blob을 삭제해 고아 Blob을 막습니다.
- **삭제**: `@Transactional` 안에서 DB 삭제(flush) → Blob 삭제. Blob 삭제가 실패하면 **롤백**되어 메타데이터가 남고 사용자가 재시도할 수 있습니다.
  이 동작은 `@MockitoSpyBean`으로 스토리지 장애를 주입하는 통합 테스트로 검증했고, 실제로 Azurite를 중지해 502 응답과 메타데이터 보존을 확인했습니다.
- 운영 고도화: soft delete + 비동기 정리 배치, Outbox 패턴, Blob soft delete/versioning 활성화.

### ③ 파일 처리 보안과 검증
- 확장자 **화이트리스트** + **매직 넘버(파일 시그니처)** 검사: `virus.exe`를 `photo.png`로 바꿔 올리는 위장 업로드 차단
- `Content-Type`은 클라이언트 값을 신뢰하지 않고 서버가 확장자로 결정
- 경로 조작(`../`, `C:\`) 제거, Blob 이름은 **UUID 기반**(원본명은 DB에만) → 덮어쓰기·인코딩·추측 공격 방지, `If-None-Match: *`로 덮어쓰기 이중 방지
- 10MB 제한을 multipart 설정 + 검증기 + 프론트 사전 검증의 3단계로 적용, `server.tomcat.max-swallow-size`로 초과 요청에도 413 JSON 응답 보장
- 비밀값은 코드에 없음(환경변수), CORS는 origin 화이트리스트, 오류는 내부 정보를 숨긴 RFC 9457 형식

### ④ 스트리밍 기반 업·다운로드와 RESTful API
- 다운로드는 `BlobClient.openInputStream()` → `InputStreamResource`로 **청크 스트리밍**하여 서버 메모리 사용량이 파일 크기와 무관합니다.
  Blob 존재 여부를 응답 헤더를 쓰기 전에 판단하므로 404/502를 정확히 돌려줍니다.
- nginx `proxy_request_buffering off` / `proxy_buffering off`로 프록시 구간도 스트리밍
- 한글 파일명은 RFC 5987 `filename*=UTF-8''...`로 인코딩해 브라우저별로 원본 이름 유지
- `201 + Location`, `204`, 하위 리소스 다운로드, 쿼리 파라미터 검색·페이지네이션, DTO로 엔티티 비노출

### ⑤ 컨테이너 기반 실행 환경과 운영 관점
- **healthcheck 기반 기동 순서**(MySQL TCP ping → Backend `/api/health` → Frontend)
- 스토리지 미연결 시 **fail-fast**, Health는 DOWN 시 503 반환 → 오케스트레이터(Compose / Container Apps / AKS)가 감지 가능
- SDK **지수 백오프 재시도 정책**으로 일시 장애(429/503)에 대응
- 멀티 스테이지 빌드(JDK로 빌드 → JRE 런타임, non-root 사용자), Apple Silicon(arm64) 네이티브 이미지
- 계층 분리(Controller/Service/Repository/Storage) + `FileStorage` 인터페이스로 테스트 가능한 구조

---

## 7. 로컬 Azurite와 실제 Azure Storage의 차이점

| 항목 | Azurite (로컬) | Azure Storage (클라우드) |
|---|---|---|
| 엔드포인트 | **path-style** `http://azurite:10000/devstoreaccount1/<container>` | **host-style** `https://<account>.blob.core.windows.net/<container>` |
| 프로토콜 | HTTP | HTTPS (TLS 1.2+ 강제 권장) |
| 인증 | 공개된 고정 개발 계정/키 (`devstoreaccount1`) | 계정 키, SAS 토큰, **Microsoft Entra ID(Managed Identity)** |
| 내구성 / 복제 | 로컬 디스크(Docker 볼륨) 1벌 | LRS / ZRS / GRS / RA-GRS 복제 |
| 성능·제한 | 스로틀링 없음 | 계정·Blob별 처리량 한도, 초과 시 503/429 → SDK 재시도 |
| 고급 기능 | 미지원 또는 부분 지원 | Access tier(Hot/Cool/Cold/Archive), 수명 주기 관리, soft delete, versioning, immutable storage, 암호화 키(CMK) |
| 네트워크 보안 | 없음 | 방화벽, VNet, Private Endpoint |
| API 버전 | 최신 SDK의 API 버전을 늦게 지원할 수 있음 → `--skipApiVersionCheck` 사용 | 항상 최신 API 버전 지원 |
| 모니터링 / 비용 | 없음 / 무료 | Azure Monitor·진단 로그 / 용량·트랜잭션·송신 트래픽 과금 |

### 실제 Azure Storage로 전환하는 방법 (배포는 이번 범위 아님)

1. Azure Portal → Storage account 생성 → **Access keys**에서 연결 문자열 복사
2. `.env` 수정

   ```bash
   AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=https;AccountName=<account>;AccountKey=<key>;EndpointSuffix=core.windows.net
   ```
3. `docker compose up -d backend` → `/api/health`의 `storageInfo.provider`가 `Azure Blob Storage`로 표시됩니다.

**운영 권장 사항**: 계정 키 대신 `azure-identity`의 `DefaultAzureCredential` + Managed Identity(키 없는 인증), 비밀값은 Azure Key Vault, 대용량 파일은 SAS URL을 발급해 브라우저가 Blob에 직접 업로드, 배포는 Azure Container Apps / App Service + Azure Database for MySQL.

---

## 8. 알려진 제한 사항과 개선 방향

- 인증·권한 관리는 요구 범위에서 제외했습니다. (확장 시 Spring Security + Entra ID OAuth2)
- 스토리지 장애 시 SDK 재시도 때문에 실패 응답까지 시간이 걸립니다(로컬에서 Azurite 중지 시 삭제 요청이 약 34초 후 502 반환). 운영에서는 작업별 타임아웃과 서킷 브레이커로 조정합니다.
- 삭제 시 Blob 삭제는 성공했지만 DB 커밋이 실패하는 경우 메타데이터만 남을 수 있습니다(다운로드 시 404로 처리). soft delete + 정리 배치로 보완할 수 있습니다.
- 스키마는 `ddl-auto: update`로 단순화했습니다. 운영에서는 Flyway 같은 마이그레이션 도구를 사용합니다.
- Spring Boot BOM이 Netty 버전을 관리하기 때문에 Azure SDK가 기동 시 Netty 버전 불일치 INFO 로그를 남깁니다. 기능에는 영향이 없습니다(테스트 통과).
- Docker 이미지 빌드 시 통합 테스트는 건너뜁니다(Azurite가 필요하므로). 테스트는 위의 "테스트 실행"처럼 별도로 실행합니다.
