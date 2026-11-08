# Build: Maven + JDK 21 (versión fija). Las pruebas se ejecutan en el pipeline (necesitan Docker para PostgreSQL).
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests -Djacoco.skip=true

# Runtime: solo JRE 21, usuario no root, sin secretos (todo entra por variables de entorno)
FROM eclipse-temurin:21.0.8_9-jre-noble
RUN groupadd --system ms04 && useradd --system --gid ms04 --no-create-home ms04
WORKDIR /app
COPY --from=build /workspace/target/evacuation-service-*.jar app.jar
USER ms04
# Puerto interno de MS04 (Estándares, sección 2). No se publica hacia fuera: el acceso externo es por el Gateway.
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
