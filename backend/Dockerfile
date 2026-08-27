# 1. Build Stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# 의존성 매니페스트를 먼저 복사해 캐시 레이어를 만든다.
# src가 바뀌어도 이 레이어는 재사용되므로 매 배포마다 의존성을 다시 받지 않는다.
COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null

# 소스 변경 시 여기서부터 재실행된다.
# test를 명시해야 실제로 돈다. bootJar 단독은 test를 태스크 그래프에 넣지 않는다.
COPY src ./src
RUN ./gradlew --no-daemon clean test bootJar

# 2. Run Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 비루트 실행.
RUN addgroup -S app && adduser -S -G app app
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar
USER app

# 컨테이너 메모리 한도를 JVM이 인식하게 한다. Render 무료 티어는 512MB다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
