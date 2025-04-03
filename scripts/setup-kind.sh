#!/bin/bash
set -e

# Variables
CLUSTER_NAME="otelbrot"
INGRESS_NGINX_VERSION="4.8.3"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Setting up kind cluster for Otelbrot${NC}"

# Check if kind is installed
if ! command -v kind &> /dev/null; then
    echo -e "${RED}kind is not installed. Please install kind first:${NC}"
    echo "https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
    exit 1
fi

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}kubectl is not installed. Please install kubectl first:${NC}"
    echo "https://kubernetes.io/docs/tasks/tools/install-kubectl/"
    exit 1
fi

# Check if helm is installed
if ! command -v helm &> /dev/null; then
    echo -e "${RED}helm is not installed. Please install helm first:${NC}"
    echo "https://helm.sh/docs/intro/install/"
    exit 1
fi

# Check if the cluster already exists
if kind get clusters | grep -q "${CLUSTER_NAME}"; then
    echo -e "${YELLOW}Cluster '${CLUSTER_NAME}' already exists. Skipping creation.${NC}"
else
    echo -e "${GREEN}Creating kind cluster with ingress support...${NC}"
    # Create a simplified kind config file with minimal required settings for ARM64
    cat <<EOF > kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
      - containerPort: 3000
        hostPort: 3000
        protocol: TCP
  - role: worker
  - role: worker
EOF

    # Create the cluster with verbose logging
    echo -e "${GREEN}Creating cluster with verbose logging...${NC}"
    kind create cluster --name "${CLUSTER_NAME}" --config=kind-config.yaml --verbosity 999 || {
        echo -e "${RED}Cluster creation failed. Checking Docker container logs...${NC}"
        docker ps -a | grep "${CLUSTER_NAME}"
        CONTAINER_ID=$(docker ps -a --filter name="${CLUSTER_NAME}-control-plane" --format "{{.ID}}")
        if [ -n "$CONTAINER_ID" ]; then
            echo -e "${YELLOW}Control plane container logs:${NC}"
            docker logs "$CONTAINER_ID"
        else
            echo -e "${RED}Control plane container not found${NC}"
        fi
        exit 1
    }
    rm kind-config.yaml
fi

# Install Nginx Ingress Controller using the latest method from the kind documentation
echo -e "${GREEN}Installing NGINX Ingress Controller...${NC}"
kubectl apply -f https://kind.sigs.k8s.io/examples/ingress/deploy-ingress-nginx.yaml

echo -e "${GREEN}Waiting for ingress controller to be ready...${NC}"
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

# Set up hosts entries
echo -e "${YELLOW}Adding local hosts entries${NC}"
echo "To access the application, you need to add the following entries to your /etc/hosts file:"
echo "127.0.0.1 otelbrot.local metrics.otelbrot.local"
echo ""
echo "Run the following command to add these entries automatically:"
echo "sudo bash -c 'echo \"127.0.0.1 otelbrot.local metrics.otelbrot.local\" >> /etc/hosts'"

# Install OpenTelemetry Operator with Helm
echo -e "${GREEN}Setting up cert-manager...${NC}"
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm upgrade --install cert-manager jetstack/cert-manager \
    --namespace cert-manager \
    --create-namespace \
    --set installCRDs=true

echo -e "${GREEN}Waiting for cert-manager to be ready...${NC}"
kubectl wait --namespace cert-manager --for=condition=ready pod --selector=app.kubernetes.io/instance=cert-manager --timeout=120s

echo -e "${GREEN}Setting up OpenTelemetry Operator...${NC}"
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm upgrade --install opentelemetry-operator open-telemetry/opentelemetry-operator \
    --create-namespace \
    --namespace opentelemetry-operator-system \
    -f ../helm-charts/otel-operator-values.yaml

# Wait for all resources to be ready
echo -e "${GREEN}Waiting for all resources to be ready...${NC}"
kubectl wait --namespace opentelemetry-operator-system --for=condition=ready pod --selector=app.kubernetes.io/instance=opentelemetry-operator --timeout=120s

# Create namespaces
echo -e "${GREEN}Creating namespaces...${NC}"
kubectl create namespace otelbrot --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -

echo -e "${GREEN}Kind cluster setup is complete.${NC}"
echo -e "${YELLOW}You can now deploy the application using:${NC}"
echo "make helm-deploy"
echo ""
echo -e "${YELLOW}After deployment, access the application at:${NC}"
echo "http://otelbrot.local"
echo -e "${YELLOW}And the metrics dashboard at:${NC}"
echo "http://metrics.otelbrot.local"