FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pms-legado-mock/pom.xml ./pom.xml
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY pms-legado-mock/src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests \
    dependency:copy-dependencies -DoutputDirectory=target/dependency

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/pms-legado-mock-1.0-SNAPSHOT.jar /app/mock.jar
COPY --from=build /build/target/dependency /app/dependency
ENTRYPOINT ["java", "-cp", "/app/mock.jar:/app/dependency/*"]
