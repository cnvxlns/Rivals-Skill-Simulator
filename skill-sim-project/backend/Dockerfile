# 1. Maven과 Java 17이 미리 설치된 이미지를 사용합니다. (mvnw 필요 없음!)
FROM maven:3.9.6-eclipse-temurin-17-alpine

# 2. 작업 폴더 설정
WORKDIR /app

# 3. 프로젝트 전체 복사
COPY . .

# 4. 빌드 실행 (mvnw 대신 그냥 mvn 명령어를 씁니다)
RUN mvn clean package -DskipTests

# 5. 실행 (target 폴더 안에 생긴 jar 파일을 실행)
# 주의: 실제 생성되는 파일명에 따라 이름이 다를 수 있으니 와일드카드(*) 사용
CMD ["sh", "-c", "java -jar target/*.jar"]