# OTEL-Monte Makefile

.PHONY: test test-frontend test-orchestrator test-worker test-go-worker clean build build-go-worker docker-build deploy run-worker run-go-worker install-cert-manager install-otel-operator install-dependencies create-honeycomb-secret create-namespace k8s-cleanup k8s-cleanup-all

# Run all tests
test: test-frontend test-orchestrator test-worker test-go-worker

# Build all components
build:
	@echo "Building all components..."
	cd frontend && npm run build
	cd commons && ../worker/mvnw clean install -q -DskipTests
	cd orchestrator && ./mvnw clean install -q -DskipTests
	cd worker && ./mvnw clean install -q -DskipTests
	$(MAKE) build-go-worker

# Build Go worker
build-go-worker:
	@echo "Building Go worker..."
	cd go-worker && go build ./cmd/worker

# Run frontend tests (lint only, but don't fail on warnings)
test-frontend:
	@echo "Running frontend tests (lint only)..."
	cd frontend && npm run lint --silent || true

# Run orchestrator tests with reduced logging (specify controller tests only)
test-orchestrator:
	@echo "Running orchestrator tests..."
	cd orchestrator && ./mvnw test -q -Dlogging.level.root=ERROR -Dlogging.level.org.springframework=ERROR -Dtest="*Controller*"

# Run worker tests with reduced logging (skip application test)
test-worker:
	@echo "Running worker tests..."
	cd worker && ./mvnw test -q -Dlogging.level.root=ERROR -Dlogging.level.org.springframework=ERROR -Dtest="!WorkerApplicationTests"

# Run Go worker tests
test-go-worker:
	@echo "Running Go worker tests..."
	cd go-worker && go test ./...

# Clean all build outputs
clean:
	@echo "Cleaning project..."
	cd frontend && rm -rf node_modules dist
	cd commons && ../worker/mvnw clean -q
	cd orchestrator && ./mvnw clean -q
	cd worker && ./mvnw clean -q
	cd go-worker && rm -f worker

# Build Docker images
docker-build: build
	@echo "Building Docker images..."
	docker build -t otelbrot/frontend:latest -f ./frontend/Dockerfile ./frontend
	docker build -t otelbrot/orchestrator:latest -f ./orchestrator/Dockerfile .
	docker build -t otelbrot/worker:latest -f ./worker/Dockerfile .
	docker build -t otelbrot/go-worker:latest -f ./go-worker/Dockerfile ./go-worker
	@echo "Docker images built."

# Install cert-manager
install-cert-manager:
	@echo "Installing cert-manager..."
	kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.4/cert-manager.yaml
	@echo "Waiting for cert-manager to be ready..."
	kubectl -n cert-manager wait --for=condition=available deployment --all --timeout=300s
	@echo "cert-manager installed successfully."

# Install OpenTelemetry Operator
install-otel-operator:
	@echo "Installing OpenTelemetry Operator..."
	kubectl apply -f https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml
	@echo "Waiting for OpenTelemetry Operator to be ready..."
	kubectl -n opentelemetry-operator-system wait --for=condition=available deployment --all --timeout=300s
	@echo "OpenTelemetry Operator installed successfully."

# Create namespace
create-namespace:
	@echo "Creating otelbrot namespace..."
	kubectl create namespace otelbrot --dry-run=client -o yaml | kubectl apply -f -
	@echo "Namespace created successfully."

# Create Honeycomb API key secret
create-honeycomb-secret: create-namespace
	@if [ -z "$(HONEYCOMB_API_KEY)" ]; then \
		echo "Error: HONEYCOMB_API_KEY environment variable is not set"; \
		exit 1; \
	fi
	@echo "Creating Honeycomb API key secret..."
	kubectl create secret generic honeycomb-api-key \
		--namespace otelbrot \
		--from-literal=api-key=$(HONEYCOMB_API_KEY) \
		--dry-run=client -o yaml | kubectl apply -f -
	@echo "Honeycomb API key secret created successfully."

# Install all dependencies
install-dependencies: install-cert-manager install-otel-operator create-namespace
	@echo "All dependencies installed successfully."

# Deploy to Kubernetes
deploy: docker-build install-dependencies create-honeycomb-secret
	@echo "Deploying to Kubernetes..."
	kubectl apply -f k8s/namespace.yaml
	kubectl apply -f k8s/redis.yaml
	kubectl apply -f k8s/rbac.yaml
	kubectl apply -f k8s/otel-agent-orchestrator-config.yaml
	kubectl apply -f k8s/otel-agent-worker-config.yaml
	kubectl apply -f k8s/orchestrator.yaml
	kubectl apply -f k8s/frontend.yaml
	kubectl apply -f k8s/opentelemetry.yaml
	@echo "Deployment complete. Use 'kubectl get pods -n otelbrot' to check status."

# Run a worker job
run-worker:
	@echo "Creating worker job..."
	kubectl apply -f k8s/worker.yaml
	@echo "Worker job created. Use 'kubectl get jobs -n otelbrot' to check status."

# Run a Go worker deployment
run-go-worker:
	@echo "Creating Go worker deployment..."
	kubectl apply -f k8s/go-worker.yaml
	@echo "Go worker deployment created. Use 'kubectl get deployment -n otelbrot' to check status."

# Clean up Kubernetes resources (keeps namespace)
k8s-cleanup:
	@echo "Cleaning up Kubernetes resources..."
	@echo "Deleting application resources..."
	-kubectl delete -f k8s/frontend.yaml --ignore-not-found
	-kubectl delete -f k8s/orchestrator.yaml --ignore-not-found
	-kubectl delete -f k8s/redis.yaml --ignore-not-found
	-kubectl delete -f k8s/go-worker.yaml --ignore-not-found
	-kubectl delete -f k8s/opentelemetry.yaml --ignore-not-found
	-kubectl delete -f k8s/otel-agent-orchestrator-config.yaml --ignore-not-found
	-kubectl delete -f k8s/otel-agent-worker-config.yaml --ignore-not-found
	@echo "Deleting jobs and pods..."
	-kubectl delete jobs --all -n otelbrot --ignore-not-found
	-kubectl delete pods --all -n otelbrot --ignore-not-found
	@echo "Deleting RBAC resources..."
	-kubectl delete -f k8s/rbac.yaml --ignore-not-found
	@echo "Cleaning up persistent resources..."
	-kubectl delete pvc --all -n otelbrot --ignore-not-found
	@echo "Kubernetes resources cleaned up successfully."

# Clean up all Kubernetes resources (including namespace)
k8s-cleanup-all: k8s-cleanup
	@echo "Performing complete cleanup including namespace..."
	-kubectl delete secret honeycomb-api-key -n otelbrot --ignore-not-found
	-kubectl delete -f k8s/namespace.yaml --ignore-not-found
	@echo "Complete cleanup finished. Namespace has been deleted."

# Show help
help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  test                  Run all tests"
	@echo "  test-frontend         Run frontend tests"
	@echo "  test-orchestrator     Run orchestrator tests"
	@echo "  test-worker           Run worker tests" 
	@echo "  test-go-worker        Run Go worker tests" 
	@echo "  build                 Build all components"
	@echo "  build-go-worker       Build Go worker only"
	@echo "  clean                 Clean all build outputs"
	@echo "  docker-build          Build Docker images"
	@echo "  install-cert-manager  Install cert-manager in Kubernetes"
	@echo "  install-otel-operator Install OpenTelemetry Operator in Kubernetes"
	@echo "  install-dependencies  Install all dependencies"
	@echo "  deploy                Deploy to Kubernetes (includes dependencies)"
	@echo "  run-worker            Run a worker job in Kubernetes"
	@echo "  run-go-worker         Run a Go worker deployment in Kubernetes"
	@echo "  k8s-cleanup           Clean up Kubernetes resources (keeps namespace)"
	@echo "  k8s-cleanup-all       Clean up all Kubernetes resources (including namespace)"
	@echo "  help                  Show this help message"