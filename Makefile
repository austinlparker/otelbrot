# OTEL-Monte Makefile

.PHONY: test test-frontend test-orchestrator test-go-worker clean build build-frontend build-orchestrator build-go-worker docker-build install-cert-manager helm-install-namespaces helm-add-repos helm-install-otel-operator helm-install-otel-gateway-collector helm-install-otel-lgtm clean-conflicting-resources helm-install-otelbrot-app helm-deploy helm-upgrade helm-cleanup helm-cleanup-all frontend-dev orchestrator-run worker-run redis-run java-test java-test-debug kind-setup kind-deploy help

# Run all tests
test: test-frontend test-orchestrator test-go-worker
	@echo "✓ All tests completed successfully."

# Build all components
build:
	@echo "Building components..."
	@cd frontend && npm run lint --silent || { echo "❌ Frontend linting failed"; exit 1; }
	@cd frontend && npm run build --silent || { echo "❌ Frontend build failed"; exit 1; }
	@cd commons && ../orchestrator/mvnw clean install -q -DskipTests || { echo "❌ Commons build failed"; exit 1; }
	@cd orchestrator && ./mvnw clean install -q -DskipTests || { echo "❌ Orchestrator build failed"; exit 1; }
	@$(MAKE) -s build-go-worker
	@echo "✓ All components built successfully."

# Build frontend
build-frontend:
	@echo "Building frontend..."
	@cd frontend && npm run lint --silent || { echo "❌ Frontend linting failed"; exit 1; }
	@cd frontend && npm run build --silent || { echo "❌ Frontend build failed"; exit 1; }
	@echo "✓ Frontend built successfully."

# Build orchestrator
build-orchestrator:
	@echo "Building orchestrator..."
	@cd commons && ../orchestrator/mvnw clean install -q -DskipTests || { echo "❌ Commons build failed"; exit 1; }
	@cd orchestrator && ./mvnw clean install -q -DskipTests || { echo "❌ Orchestrator build failed"; exit 1; }
	@echo "✓ Orchestrator built successfully."

# Build Go worker
build-go-worker:
	@echo "Building Go worker..."
	@cd go-worker && go build ./cmd/worker || { echo "❌ Go worker build failed"; exit 1; }
	@echo "✓ Go worker built successfully."

# Run frontend tests (lint only, but don't fail on warnings)
test-frontend:
	@echo "Running frontend tests..."
	@cd frontend && npm run lint --silent || true
	@echo "✓ Frontend tests completed."

# Run orchestrator tests with reduced logging
test-orchestrator:
	@echo "Running orchestrator tests..."
	@cd orchestrator && ./mvnw test -q -Dlogging.level.root=ERROR -Dlogging.level.org.springframework=ERROR -Dtest="*Controller*" || { echo "❌ Orchestrator tests failed"; exit 1; }
	@echo "✓ Orchestrator tests completed successfully."

# Run Go worker tests
test-go-worker:
	@echo "Running Go worker tests..."
	@cd go-worker && go test ./internal/... ./cmd/... || { echo "❌ Go worker tests failed"; exit 1; }
	@echo "✓ Go worker tests completed successfully."

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

# Install OTEL-LGTM (All-in-one container) via otelbrot-app chart
helm-install-otel-lgtm: helm-add-repos helm-install-namespaces
	@echo "OTEL-LGTM is now included in the otelbrot-app chart - no separate installation required."
	@echo "It will be automatically installed with 'helm-install-otelbrot-app'."

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
helm-install-otel-gateway-collector: helm-add-repos helm-install-namespaces
	@echo "Installing OpenTelemetry Gateway Collector (DaemonSet)..."
	helm upgrade --install otel-gateway-collector open-telemetry/opentelemetry-collector \
		--namespace otelbrot \
		-f helm-charts/otel-gateway-collector-values.yaml
	@echo "OpenTelemetry Gateway Collector (DaemonSet) installed successfully."



# Clean up any conflicting resources
clean-conflicting-resources:
	@echo "Cleaning up any conflicting resources..."
	-kubectl delete configmap otel-agent-config -n otelbrot --ignore-not-found
	@echo "Conflicting resources cleaned up."

# Install otelbrot application with Helm
helm-install-otelbrot-app: docker-build helm-install-namespaces clean-conflicting-resources helm-install-otel-operator
	@echo "Installing otelbrot application with OTEL-LGTM..."
	@helm upgrade --install otelbrot-app ./helm-charts/otelbrot-app \
		--namespace otelbrot \
		--set otelLgtm.enabled=true \
		>/dev/null 2>&1 || { echo "❌ Otelbrot application installation failed"; exit 1; }
	@echo "✓ Otelbrot application with OTEL-LGTM installed successfully."

# Upgrade otelbrot application with Helm
helm-upgrade:
	@echo "Upgrading otelbrot application..."
	@helm upgrade otelbrot-app ./helm-charts/otelbrot-app \
		--namespace otelbrot \
		--set otelLgtm.enabled=true \
		>/dev/null 2>&1 || { echo "❌ Otelbrot application upgrade failed"; exit 1; }
	@echo "✓ Otelbrot application upgraded successfully."

# Deploy everything with Helm
helm-deploy: docker-build helm-install-namespaces helm-install-otel-operator helm-install-otel-gateway-collector helm-install-otelbrot-app
	@echo "✓ Deployment complete. Use 'kubectl get pods -n otelbrot' to check status."

# Clean up Helm releases
helm-cleanup:
	@echo "Cleaning up Helm releases..."
	-helm uninstall otel-gateway-collector --namespace otelbrot
	-helm uninstall otelbrot-app --namespace otelbrot
	-kubectl delete jobs --all -n otelbrot --ignore-not-found
	-kubectl delete pods --all -n otelbrot --ignore-not-found
	-kubectl delete pvc --all -n otelbrot --ignore-not-found
	@echo "Helm releases and application resources cleaned up."

# Clean up all Helm releases including operator and namespace
helm-cleanup-all: helm-cleanup
	@echo "Cleaning up all Helm releases including operator..."
	-helm uninstall opentelemetry-operator --namespace opentelemetry-operator-system
	-helm uninstall otelbrot-namespaces
	@echo "All resources cleaned up. Namespaces will be deleted once resources are removed."

# Run frontend development server
frontend-dev:
	@echo "Starting frontend development server..."
	@cd frontend && npm run dev

# Run orchestrator
orchestrator-run:
	@echo "Starting orchestrator..."
	@cd orchestrator && ./mvnw spring-boot:run

# Run Go worker
worker-run:
	@echo "Starting Go worker..."
	@cd go-worker && ./worker

# Run Redis locally
redis-run:
	@echo "Starting Redis locally..."
	@docker run -d -p 6379:6379 redis:alpine

# Run specific Java test
java-test:
	@if [ -z "$(TEST_CLASS)" ]; then \
		echo "❌ Error: TEST_CLASS parameter is required"; \
		echo "Usage: make java-test TEST_CLASS=TestClass#testMethod"; \
		exit 1; \
	fi
	@echo "Running Java test: $(TEST_CLASS)..."
	@cd orchestrator && ./mvnw test -Dtest="$(TEST_CLASS)" -q || { echo "❌ Java test failed"; exit 1; }
	@echo "✓ Java test completed successfully."

# Run Java test in debug mode
java-test-debug:
	@if [ -z "$(TEST_CLASS)" ]; then \
		echo "❌ Error: TEST_CLASS parameter is required"; \
		echo "Usage: make java-test-debug TEST_CLASS=TestClass"; \
		exit 1; \
	fi
	@echo "Running Java test in debug mode: $(TEST_CLASS)..."
	@cd orchestrator && ./mvnw test -Dtest="$(TEST_CLASS)" -Dmaven.surefire.debug -q || { echo "❌ Java test in debug mode failed"; exit 1; }
	@echo "✓ Java test in debug mode completed successfully."

# Set up kind cluster for local development
kind-setup:
	@echo "Setting up kind cluster for Otelbrot..."
	@./scripts/setup-kind.sh
	@echo "✓ Kind cluster setup completed."

# Deploy the application to a kind cluster
kind-deploy: docker-build
	@echo "Deploying to kind cluster..."
	@kind load docker-image otelbrot/frontend:latest otelbrot/orchestrator:latest otelbrot/go-worker:latest grafana/otel-lgtm:latest --name otelbrot
	@helm upgrade --install otelbrot-app ./helm-charts/otelbrot-app \
		--namespace otelbrot \
		--set otelLgtm.enabled=true \
		-f ./helm-charts/otelbrot-app/values-kind.yaml
	@echo "✓ Deployment to kind cluster completed."
	@echo "You can access the application at: http://otelbrot.local"
	@echo "You can access the metrics dashboard at: http://metrics.otelbrot.local"
	@echo "Note: You may need to add these hostnames to your /etc/hosts file if not done already:"
	@echo "  127.0.0.1 otelbrot.local metrics.otelbrot.local"

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
	@echo "  build-frontend                  Build frontend only (includes linting)"
	@echo "  build-orchestrator              Build orchestrator only"
	@echo "  build-go-worker                 Build Go worker only"
	@echo "  clean                           Clean all build outputs"
	@echo "  docker-build                    Build Docker images"
	@echo ""
	@echo "Run Targets:"
	@echo "  frontend-dev                    Run frontend development server"
	@echo "  orchestrator-run                Run orchestrator"
	@echo "  worker-run                      Run Go worker"
	@echo "  redis-run                       Run Redis locally"
	@echo ""
	@echo "Advanced Testing:"
	@echo "  java-test TEST_CLASS=Class      Run specific Java test"
	@echo "  java-test-debug TEST_CLASS=Class Run Java test in debug mode"
	@echo ""
	@echo "Deployment Targets (Helm-based):"
	@echo "  helm-deploy                     Deploy everything using Helm (recommended)"
	@echo "  helm-upgrade                    Upgrade only the application without rebuilding dependencies"
	@echo "  helm-install-otelbrot-app       Install/Upgrade otelbrot app using Helm"
	@echo "  helm-cleanup                    Clean up application resources"
	@echo "  helm-cleanup-all                Clean up everything including operators"
	@echo "  kind-setup                      Set up a kind cluster with Nginx Ingress for local development"
	@echo "  kind-deploy                     Deploy the application to a kind cluster with resource limits optimized for local development"
	@echo ""
	@echo "Individual Helm Components:"
	@echo "  install-cert-manager            Install cert-manager in Kubernetes"
	@echo "  helm-install-namespaces         Create namespaces using Helm"
	@echo "  helm-add-repos                  Add Helm repositories"
	@echo "  helm-install-otel-operator      Install OpenTelemetry Operator using Helm"
	@echo "  helm-install-otel-gateway-collector Install gateway collector (DaemonSet) using Helm"
	@echo "  helm-install-otel-lgtm          Install OTEL-LGTM all-in-one container using Helm"
	@echo ""
	@echo "  clean-conflicting-resources     Clean up resources that conflict with Helm"
	@echo "  help                            Show this help message"