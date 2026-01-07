# 1. Base Image: 가볍고 성능 좋은 OpenJDK 17 (Alpine Linux) 사용
FROM eclipse-temurin:17-jdk-alpine

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 프로젝트 파일 복사 (backend 폴더 내용물 전체)
COPY . .

# 4. 빌드 실행 (테스트 건너뛰고 jar 파일 생성)
# 윈도우에서 작성된 파일일 경우 줄바꿈 문자 오류 방지를 위해 dos2unix 설치 및 변환 (선택사항이나 권장)
RUN apk add --no-cache dos2unix && dos2unix mvnw
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# 5. 실행 명령어 (생성된 jar 실행)
# target 폴더 안에 있는 jar 파일을 찾아서 실행
CMD ["java", "-jar", "target/skill-sim-0.0.1-SNAPSHOT.jar"]