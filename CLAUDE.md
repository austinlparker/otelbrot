# OTelBrot Development Guide

## Build Commands
- Frontend: `cd frontend && npm run build` (TypeScript + Vite)
- Frontend Dev: `cd frontend && npm run dev` (Vite dev server)
- Frontend Preview: `cd frontend && npm run preview` (Local preview build)
- Orchestrator: `cd orchestrator && ./mvnw clean install`
- Worker: `cd worker && ./mvnw clean install`
- Commons: `cd commons && ./mvnw clean install`

## Test/Lint Commands
- Frontend Lint: `cd frontend && npm run lint`
- Orchestrator Test: `cd orchestrator && ./mvnw test`
- Worker Test: `cd worker && ./mvnw test`
- Single Test: `./mvnw test -Dtest=TestClass#testMethod` (Java)
- Single Package Test: `./mvnw test -Dtest="io.aparker.otelbrot.package.*"`
- Debug Test: `./mvnw test -Dtest=TestClass -Dmaven.surefire.debug`

## Run Commands
- Frontend: `cd frontend && npm run dev`
- Orchestrator: `cd orchestrator && ./mvnw spring-boot:run`
- Worker: `cd worker && ./mvnw spring-boot:run`
- Local Redis: `docker run -d -p 6379:6379 redis:alpine`

## Code Style
- Frontend: TypeScript with React functional components (React 19), no semicolons
- Java: 4-space indentation, Java 21 features, Spring Boot microservices
- Import order: Java stdlib, third-party libs, project imports
- TypeScript: Use interfaces for types, prefer const, use arrow functions
- Testing: JUnit 5 for Java, use @ExtendWith(MockitoExtension.class)
- Error handling: Try/catch with proper spans for OpenTelemetry
- OpenTelemetry: Follow semantic conventions for spans and attributes
- Environment: Redis for orchestration, Kubernetes for worker scaling