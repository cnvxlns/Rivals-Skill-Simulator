# [Stage 1] 빌드 단계: Maven이 포함된 이미지를 사용 (mvnw 필요 없음!)
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build

# 작업 디렉토리 설정
WORKDIR /app

# 1. 의존성 파일(pom.xml)만 먼저 복사 (캐싱 효율을 위해)
COPY pom.xml .

# 2. 라이브러리 다운로드 (소스코드가 바뀌어도 이 단계는 캐시됨)
RUN mvn dependency:go-offline -B

# 3. 소스코드 복사 및 빌드 실행 (테스트 건너뛰기)
COPY src ./src
RUN mvn clean package -DskipTests

# --------------------------------------------------------

# [Stage 2] 실행 단계: 가벼운 JRE 이미지만 사용
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 빌드 단계(Stage 1)에서 생성된 JAR 파일만 쏙 가져옴
# (파일명이 달라도 *.jar로 자동으로 찾아서 app.jar로 복사함)
COPY --from=build /app/target/*.jar app.jar

# 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]