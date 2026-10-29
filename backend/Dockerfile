# ---------- Build stage ----------
# BuildKit 전용 문법(--mount)을 쓰지 않아 classic builder에서도 빌드된다
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 받아 소스만 바뀌면 재다운로드하지 않는다 (Docker 레이어 캐시)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# 통합 테스트는 Azurite가 필요하므로 이미지 빌드 시에는 건너뛴다 (README의 테스트 실행 방법 참고)
RUN mvn -B -q package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/target/clouddocs.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
