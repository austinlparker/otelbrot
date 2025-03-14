# OTEL-Monte Makefile

.PHONY: test test-frontend test-orchestrator test-go-worker clean build build-go-worker docker-build install-cert-manager create-honeycomb-secret helm-install-namespaces helm-add-repos helm-install-otel-operator helm-install-otel-gateway-collector helm-install-lgtm-distributed clean-conflicting-resources helm-install-otelbrot-app helm-deploy helm-cleanup helm-cleanup-all help

# Run all tests
test: test-frontend test-orchestrator test-go-worker

# Build all components
build:
	@echo "Building all components..."
	cd frontend && npm run build
	cd commons && ../orchestrator/mvnw clean install -q -DskipTests
	cd orchestrator && ./mvnw clean install -q -DskipTests
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


# Run Go worker tests
test-go-worker:
	@echo "Running Go worker tests..."
	cd go-worker && go test -v ./internal/... ./cmd/...

# Clean all build outputs
clean:
	@echo "Cleaning project..."
	cd frontend && rm -rf node_modules dist
	cd commons && ../orchestrator/mvnw clean -q
	cd orchestrator && ./mvnw clean -q
	cd go-worker && rm -f worker

# Build Docker images
docker-build: build
	@echo "Building Docker images..."
	docker build -t otelbrot/frontend:latest -f ./frontend/Dockerfile ./frontend
	docker build -t otelbrot/orchestrator:latest -f ./orchestrator/Dockerfile .
	docker build -t otelbrot/go-worker:latest -f ./go-worker/Dockerfile ./go-worker
	@echo "Docker images built."

# Install cert-manager
install-cert-manager:
	@echo "Installing cert-manager..."
	helm repo add jetstack https://charts.jetstack.io
	helm repo update
	helm upgrade --install cert-manager jetstack/cert-manager \
		--namespace cert-manager \
		--create-namespace \
		--set installCRDs=true
	@echo "Waiting for cert-manager to be ready..."
	kubectl -n cert-manager wait --for=condition=available deployment --all --timeout=300s
	@echo "cert-manager installed successfully."

# Install namespaces with Helm
helm-install-namespaces: helm-add-repos
	@echo "Installing namespaces with Helm..."
	helm upgrade --install otelbrot-namespaces ./helm-charts/namespaces
	@echo "Namespaces installed successfully."

# Create Honeycomb API key secret
create-honeycomb-secret: helm-install-namespaces
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

# Add Helm repositories
helm-add-repos:
	@echo "Adding Helm repositories..."
	helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
	helm repo add grafana https://grafana.github.io/helm-charts
	helm repo update
	@echo "Helm repositories added successfully."

# Install OpenTelemetry Operator with Helm
helm-install-otel-operator: helm-add-repos install-cert-manager
	@echo "Installing OpenTelemetry Operator..."
	helm upgrade --install opentelemetry-operator open-telemetry/opentelemetry-operator \
		--create-namespace \
		--namespace opentelemetry-operator-system \
		-f helm-charts/otel-operator-values.yaml
	@echo "Waiting for OpenTelemetry Operator to be ready..."
	kubectl -n opentelemetry-operator-system wait --for=condition=available deployment --all --timeout=300s
	@echo "OpenTelemetry Operator installed successfully."

# Install OpenTelemetry Gateway Collector (DaemonSet) with Helm
helm-install-otel-gateway-collector: helm-add-repos helm-install-namespaces create-honeycomb-secret
	@echo "Installing OpenTelemetry Gateway Collector (DaemonSet)..."
	helm upgrade --install otel-gateway-collector open-telemetry/opentelemetry-collector \
		--namespace otelbrot \
		-f helm-charts/otel-gateway-collector-values.yaml
	@echo "OpenTelemetry Gateway Collector (DaemonSet) installed successfully."

# Install LGTM Distributed with Helm
helm-install-lgtm-distributed: helm-add-repos helm-install-namespaces
	@echo "Installing LGTM Distributed..."
	helm upgrade --install lgtm grafana/lgtm-distributed \
		--namespace monitoring \
		-f helm-charts/lgtm-distributed-values.yaml
	@echo "LGTM Distributed installed successfully."


# Clean up any conflicting resources
clean-conflicting-resources:
	@echo "Cleaning up any conflicting resources..."
	-kubectl delete configmap otel-agent-config -n otelbrot --ignore-not-found
	@echo "Conflicting resources cleaned up."

# Install otelbrot application with Helm
helm-install-otelbrot-app: docker-build helm-install-namespaces clean-conflicting-resources helm-install-otel-operator
	@echo "Installing otelbrot application with Helm..."
	helm upgrade --install otelbrot-app ./helm-charts/otelbrot-app \
		--namespace otelbrot
	@echo "Otelbrot application installed successfully."

# Deploy everything with Helm
helm-deploy: docker-build helm-install-namespaces helm-install-otel-operator helm-install-lgtm-distributed helm-install-otel-gateway-collector helm-install-otelbrot-app
	@echo "Deployment complete. Use 'kubectl get pods -n otelbrot' to check status."

# Clean up Helm releases
helm-cleanup:
	@echo "Cleaning up Helm releases..."
	-helm uninstall otel-gateway-collector --namespace otelbrot
	-helm uninstall lgtm --namespace monitoring
	-helm uninstall otelbrot-app --namespace otelbrot
	-kubectl delete jobs --all -n otelbrot --ignore-not-found
	-kubectl delete pods --all -n otelbrot --ignore-not-found
	-kubectl delete pvc --all -n otelbrot --ignore-not-found
	@echo "Helm releases and application resources cleaned up."

# Clean up all Helm releases including operator and namespace
helm-cleanup-all: helm-cleanup
	@echo "Cleaning up all Helm releases including operator..."
	-helm uninstall opentelemetry-operator --namespace opentelemetry-operator-system
	-kubectl delete secret honeycomb-api-key -n otelbrot --ignore-not-found
	-helm uninstall otelbrot-namespaces
	@echo "All resources cleaned up. Namespaces will be deleted once resources are removed."

# Show help
help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Development Targets:"
	@echo "  test                            Run all tests"
	@echo "  test-frontend                   Run frontend tests"
	@echo "  test-orchestrator               Run orchestrator tests"
	@echo "  test-go-worker                  Run Go worker tests" 
	@echo "  build                           Build all components"
	@echo "  build-go-worker                 Build Go worker only"
	@echo "  clean                           Clean all build outputs"
	@echo "  docker-build                    Build Docker images"
	@echo ""
	@echo "Deployment Targets (Helm-based):"
	@echo "  helm-deploy                     Deploy everything using Helm (recommended)"
	@echo "  helm-cleanup                    Clean up application resources"
	@echo "  helm-cleanup-all                Clean up everything including operators"
	@echo ""
	@echo "Individual Helm Components:"
	@echo "  install-cert-manager            Install cert-manager in Kubernetes"
	@echo "  create-honeycomb-secret         Create secret for Honeycomb API key"
	@echo "  helm-install-namespaces         Create namespaces using Helm"
	@echo "  helm-add-repos                  Add Helm repositories"
	@echo "  helm-install-otel-operator      Install OpenTelemetry Operator using Helm"
	@echo "  helm-install-otel-gateway-collector Install gateway collector (DaemonSet) using Helm"
	@echo "  helm-install-lgtm-distributed   Install LGTM Distributed using Helm"
	@echo "  helm-install-otelbrot-app       Install otelbrot app using Helm"
	@echo ""
	@echo "  clean-conflicting-resources     Clean up resources that conflict with Helm"
	@echo "  help                            Show this help message"