# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy all pom.xml files first for better layer caching
COPY pom.xml .
COPY commons/pom.xml commons/
COPY worker/pom.xml worker/
COPY orchestrator/pom.xml orchestrator/

# Create directories needed by Maven for multi-module build
RUN mkdir -p commons/src/main/java commons/src/main/resources \
    worker/src/main/java worker/src/main/resources \
    orchestrator/src/main/java orchestrator/src/main/resources

# Download all required dependencies first
RUN mvn -B dependency:go-offline

# Copy source code for commons
COPY commons/src commons/src/

# Build commons
RUN mvn -B clean install -pl commons -DskipTests

# Copy worker source code
COPY worker/src worker/src/

# Build the worker application
RUN mvn -B clean package -pl worker -DskipTests

# Stage 2: Create final image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy JAR file from build stage
COPY --from=build /app/worker/target/*.jar app.jar

# Set environment variables
ENV JAVA_TOOL_OPTIONS="-Dotel.javaagent.debug=true"

# Run the jar file, this will exit after completion
ENTRYPOINT ["java", "-jar", "/app/app.jar"]