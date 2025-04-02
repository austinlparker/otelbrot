# OTelBrot Development Guide

> **IMPORTANT**: Always run all commands from the project root directory.

## Build Commands (Make)
- All Components: `make build` (preferred method)
- Frontend Only: `make build-frontend` (includes linting)
- Orchestrator Only: `make build-orchestrator`
- Go Worker Only: `make build-go-worker`
- Docker Images: `make docker-build`

## Test Commands (Make)
- All Tests: `make test` (preferred method)
- Frontend Tests: `make test-frontend`
- Orchestrator Tests: `make test-orchestrator`
- Go Worker Tests: `make test-go-worker`

## Run Commands (Make)
Use the following commands from the project root directory:
- Run Frontend Dev Server: `make frontend-dev` (runs Vite dev server)
- Run Orchestrator: `make orchestrator-run`
- Run Go Worker: `make worker-run`
- Run Redis Locally: `make redis-run`

## Advanced Testing (Java)
- Single Test: `make java-test TEST_CLASS=TestClass#testMethod`
- Package Test: `make java-test TEST_CLASS="io.aparker.otelbrot.package.*"`
- Debug Test: `make java-test-debug TEST_CLASS=TestClass`

## Deployment (Make)
- Full Deployment: `make helm-deploy` (preferred method, installs all components)
- Application Only: `make helm-install-otelbrot-app` (installs/upgrades app only)
- Quick Upgrade: `make helm-upgrade` (upgrades app without rebuilding dependencies)
- Cleanup: `make helm-cleanup` (removes application resources)
- Full Cleanup: `make helm-cleanup-all` (removes everything including operators)

## Direct Deployment (Alternative)
- Helm: Update and deploy with `helm upgrade --install otelbrot-app ./helm-charts/otelbrot-app -n otelbrot`
- Orchestrator logging: Configured in Helm values with reduced verbosity for OrchestrationService

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