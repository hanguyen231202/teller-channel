# Bước 1: build jar bằng Maven (image có sẵn JDK 21 + Maven)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy pom trước và tải dependency: lớp này được cache, sửa code không phải tải lại
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

# Bước 2: image chạy chỉ cần JRE, nhỏ hơn và không chứa mã nguồn / Maven
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/*.jar app.jar
# Không chạy bằng root
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
