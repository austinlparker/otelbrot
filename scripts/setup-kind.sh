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
    # Create a kind config file with extraPortMappings and optimized for powerful ARM64 machine with 128 cores
    cat <<EOF > kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
# Optimize for ARM64 performance
networking:
  apiServerAddress: "127.0.0.1"
  apiServerPort: 6443
  podSubnet: "10.244.0.0/16"
  serviceSubnet: "10.96.0.0/12"
kubeadmConfigPatches:
  - |
    kind: ClusterConfiguration
    apiServer:
      extraArgs:
        enable-admission-plugins: "NodeRestriction"
        default-watch-cache-size: "1000"
        default-watch-cache: "true"
        feature-gates: "HPAScaleToZero=true"
        max-requests-inflight: "1500"
        max-mutating-requests-inflight: "500"
    controllerManager:
      extraArgs:
        large-cluster-size-threshold: "100"
        node-monitor-grace-period: "10s"
        node-cidr-mask-size-ipv4: "24"
        feature-gates: "HPAScaleToZero=true"
        concurrent-gc-syncs: "40"
        pod-eviction-timeout: "3m"
    scheduler:
      extraArgs:
        feature-gates: "HPAScaleToZero=true"
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
            system-reserved: "memory=8Gi,cpu=8"
            kube-reserved: "memory=8Gi,cpu=8"
            max-pods: "220"
            eviction-hard: "memory.available<8Gi"
            feature-gates: "HPAScaleToZero=true"
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
    # Add resource limits for control-plane
    extraMounts:
      - hostPath: /tmp/kind-control-plane
        containerPath: /var/lib/containerd
  # Add multiple worker nodes to utilize the powerful hardware - optimized for 128 cores
  # Worker node 1-4: Generic purpose (apps, monitoring, etc.)
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=app"
            system-reserved: "memory=4Gi,cpu=4"
            kube-reserved: "memory=4Gi,cpu=4"
            max-pods: "220"
            eviction-hard: "memory.available<4Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=app"
            system-reserved: "memory=4Gi,cpu=4"
            kube-reserved: "memory=4Gi,cpu=4"
            max-pods: "220"
            eviction-hard: "memory.available<4Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=app"
            system-reserved: "memory=4Gi,cpu=4"
            kube-reserved: "memory=4Gi,cpu=4"
            max-pods: "220"
            eviction-hard: "memory.available<4Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=app"
            system-reserved: "memory=4Gi,cpu=4"
            kube-reserved: "memory=4Gi,cpu=4"
            max-pods: "220"
            eviction-hard: "memory.available<4Gi"
            feature-gates: "HPAScaleToZero=true"
  # Worker node 5-12: Dedicated to fractal calculation jobs
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=compute"
            system-reserved: "memory=2Gi,cpu=2"
            kube-reserved: "memory=2Gi,cpu=2"
            max-pods: "220"
            eviction-hard: "memory.available<2Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=compute"
            system-reserved: "memory=2Gi,cpu=2"
            kube-reserved: "memory=2Gi,cpu=2"
            max-pods: "220"
            eviction-hard: "memory.available<2Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=compute"
            system-reserved: "memory=2Gi,cpu=2"
            kube-reserved: "memory=2Gi,cpu=2"
            max-pods: "220"
            eviction-hard: "memory.available<2Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=compute"
            system-reserved: "memory=2Gi,cpu=2"
            kube-reserved: "memory=2Gi,cpu=2"
            max-pods: "220"
            eviction-hard: "memory.available<2Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=compute"
            system-reserved: "memory=2Gi,cpu=2"
            kube-reserved: "memory=2Gi,cpu=2"
            max-pods: "220"
            eviction-hard: "memory.available<2Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=compute"
            system-reserved: "memory=2Gi,cpu=2"
            kube-reserved: "memory=2Gi,cpu=2"
            max-pods: "220"
            eviction-hard: "memory.available<2Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=compute"
            system-reserved: "memory=2Gi,cpu=2"
            kube-reserved: "memory=2Gi,cpu=2"
            max-pods: "220"
            eviction-hard: "memory.available<2Gi"
            feature-gates: "HPAScaleToZero=true"
  - role: worker
    kubeadmConfigPatches:
      - |
        kind: JoinConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "node.kubernetes.io/worker=true,workload=compute"
            system-reserved: "memory=2Gi,cpu=2"
            kube-reserved: "memory=2Gi,cpu=2"
            max-pods: "220"
            eviction-hard: "memory.available<2Gi"
            feature-gates: "HPAScaleToZero=true"
EOF

    # Create the cluster
    kind create cluster --name "${CLUSTER_NAME}" --config=kind-config.yaml
    rm kind-config.yaml
fi

# Install Nginx Ingress Controller
echo -e "${GREEN}Installing NGINX Ingress Controller...${NC}"
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v${INGRESS_NGINX_VERSION}/deploy/static/provider/kind/deploy.yaml

echo -e "${GREEN}Waiting for ingress controller to be ready...${NC}"
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s

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