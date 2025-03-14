# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy all pom.xml files first for better layer caching
COPY pom.xml .
COPY commons/pom.xml commons/
COPY orchestrator/pom.xml orchestrator/

# Create directories needed by Maven for multi-module build
RUN mkdir -p commons/src/main/java commons/src/main/resources \
    orchestrator/src/main/java orchestrator/src/main/resources

# Download all required dependencies first
RUN mvn -B dependency:go-offline

# Copy source code for commons
COPY commons/src commons/src/

# Build commons
RUN mvn -B clean install -pl commons -DskipTests

# Copy orchestrator source code
COPY orchestrator/src orchestrator/src/

# Build the orchestrator application
RUN mvn -B clean package -pl orchestrator -DskipTests

# Stage 2: Create final image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy JAR file from build stage
COPY --from=build /app/orchestrator/target/*.jar app.jar

# Default to prod environment
ENV SPRING_PROFILES_ACTIVE=prod

# Expose port the application runs on
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the jar file
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
