# Azure CloudDocs

Azure Blob Storage 기반 기업용 문서 관리 웹 서비스입니다.
파일은 Azure Blob Storage에, 메타데이터는 MySQL에 저장합니다. 로컬에서는 Azurite 에뮬레이터로 Azure 계정 없이 실행됩니다.

## 주요 기능

- 파일 업로드 (PDF, PNG, JPG, TXT / 최대 10MB)
- 파일 목록 조회, 파일명 검색, 최신 업로드 순 정렬
- 파일 다운로드 (원본 파일명 유지)
- 파일 삭제 (Blob + 메타데이터)
- 대시보드 (전체 파일 수, 전체 용량, 최근 업로드)

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Maven |
| Frontend | React 18, TypeScript, Vite |
| Database | MySQL 8.4 |
| Storage | Azure Storage Blob SDK for Java, Azurite |
| Infra | Docker Compose, nginx |

## 아키텍처

```
Browser ──▶ frontend (nginx :3100)
              ├─ React 정적 파일
              └─ /api ──▶ backend (Spring Boot :8080)
                             ├─ MySQL     : 파일 메타데이터
                             └─ Azurite   : 파일 바이너리 (Azure Blob SDK)
```

## 시작하기

**요구 사항**: Docker Desktop

```bash
git clone https://github.com/Heeseon0688/azure-clouddocs.git
cd azure-clouddocs
docker compose up --build
```

| 서비스 | 주소 |
|---|---|
| Web | http://localhost:3100 |
| API | http://localhost:8081/api/health |
| Azurite | http://localhost:10000 |
| MySQL | localhost:3306 |

```bash
docker compose down      # 중지
docker compose down -v   # 중지 + 데이터 삭제
```

## 환경 변수

`.env` 파일 없이도 로컬 기본값으로 실행됩니다. 변경하려면 `.env.example`을 복사해 수정하세요.

```bash
cp .env.example .env
```

| 변수 | 설명 | 기본값 |
|---|---|---|
| `AZURE_STORAGE_CONNECTION_STRING` | Azure Storage 연결 문자열 | Azurite 개발 계정 |
| `AZURE_STORAGE_CONTAINER` | Blob 컨테이너 이름 | `documents` |
| `FRONTEND_PORT` / `BACKEND_PORT` | 호스트 포트 | `3100` / `8081` |
| `MYSQL_PORT` / `MYSQL_PASSWORD` | MySQL 포트 / 비밀번호 | `3306` / `clouddocs-local` |
| `CORS_ALLOWED_ORIGINS` | 허용 Origin | `http://localhost:3100,http://localhost:5173` |

실제 Azure Storage를 사용하려면 연결 문자열만 교체합니다.

```bash
AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=https;AccountName=<account>;AccountKey=<key>;EndpointSuffix=core.windows.net
```

## API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/health` | DB · Storage 상태 확인 |
| GET | `/api/files?keyword=&page=0&size=20` | 파일 목록 / 검색 |
| POST | `/api/files` | 파일 업로드 (multipart `file`) |
| GET | `/api/files/{id}` | 파일 정보 조회 |
| GET | `/api/files/{id}/download` | 파일 다운로드 |
| DELETE | `/api/files/{id}` | 파일 삭제 |
| GET | `/api/dashboard/summary` | 대시보드 통계 |

오류는 RFC 9457 Problem Details 형식으로 반환합니다.

```json
{ "title": "Invalid file", "status": 400, "detail": "지원하지 않는 파일 형식입니다. (허용: PDF, PNG, JPG, TXT)" }
```

## 테스트

통합 테스트는 Azurite가 필요합니다.

```bash
docker compose up -d azurite
cd backend && ./mvnw test
```

Java가 설치되어 있지 않다면 Docker로 실행할 수 있습니다.

```bash
docker compose up -d azurite
docker run --rm --network azure-clouddocs_default \
  -v "$PWD/backend":/app -v clouddocs-m2:/root/.m2 -w /app \
  -e 'AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://azurite:10000/devstoreaccount1;' \
  maven:3.9-eclipse-temurin-21 mvn -B test
```

## 프로젝트 구조

```
.
├── backend/                 Spring Boot API
│   └── src/main/java/com/clouddocs
│       ├── controller/
│       ├── service/
│       ├── storage/         Azure Blob Storage 연동
│       ├── repository/
│       ├── domain/
│       ├── dto/
│       ├── exception/
│       └── config/
├── frontend/                React + TypeScript
├── compose.yaml
└── .env.example
```
