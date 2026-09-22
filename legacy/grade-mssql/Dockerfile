# 성적 집계 호출부 — multi-stage (gradle 로 빌드 → JRE 로 실행). compose 서비스 grade, 8083→8080
FROM gradle:8.10-jdk21 AS build
WORKDIR /src
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle bootJar --no-daemon -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar
ENV GRADES_JDBC_URL="jdbc:sqlserver://mssql:1433;databaseName=grades;encrypt=true;trustServerCertificate=true"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
