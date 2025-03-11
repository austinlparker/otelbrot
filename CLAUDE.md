# OTelBrot Development Guide

## Build Commands
- Frontend: `cd frontend && npm run build` (TypeScript + Vite)
- Frontend Dev: `cd frontend && npm run dev` (Vite dev server)
- Frontend Preview: `cd frontend && npm run preview` (Local preview build)
- Orchestrator: `cd orchestrator && ./mvnw clean install`
- Go Worker: `cd go-worker && go build ./cmd/worker`
- Commons: `cd commons && ./mvnw clean install`

## Test/Lint Commands
- Frontend Lint: `cd frontend && npm run lint`
- Orchestrator Test: `cd orchestrator && ./mvnw test`
- Go Worker Test: `cd go-worker && go test -v ./internal/... ./cmd/...`
- Single Test (Java): `./mvnw test -Dtest=TestClass#testMethod`
- Single Package Test (Java): `./mvnw test -Dtest="io.aparker.otelbrot.package.*"`
- Debug Test (Java): `./mvnw test -Dtest=TestClass -Dmaven.surefire.debug`

## Run Commands
- Frontend: `cd frontend && npm run dev`
- Orchestrator: `cd orchestrator && ./mvnw spring-boot:run`
- Go Worker: `cd go-worker && ./worker`
- Local Redis: `docker run -d -p 6379:6379 redis:alpine`

## Code Style
- Frontend: TypeScript with React functional components (React 19), no semicolons
- Java: 4-space indentation, Java 21 features, Spring Boot microservices
- Go: Follow standard Go conventions, use error handling idioms
- Import order: Java stdlib, third-party libs, project imports
- TypeScript: Use interfaces for types, prefer const, use arrow functions
- Testing: JUnit 5 for Java, use @ExtendWith(MockitoExtension.class), Go table tests
- Error handling: Try/catch with proper spans for OpenTelemetry
- OpenTelemetry: Follow semantic conventions for spans and attributes
- Environment: Redis for orchestration, Kubernetes for worker scaling