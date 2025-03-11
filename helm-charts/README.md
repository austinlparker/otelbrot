# Otelbrot Helm Charts

This directory contains Helm charts for deploying the Otelbrot application and its components.

## Charts

- **otelbrot-app**: The main application components including frontend, orchestrator, and Redis
- **otel-operator-values.yaml**: Values for the OpenTelemetry Operator Helm chart
- **otel-cluster-collector-values.yaml**: Values for the standalone OpenTelemetry Collector
- **otel-app-collector-values.yaml**: Values for the application OpenTelemetry Collector Helm chart
- **lgtm-distributed-values.yaml**: Values for the Grafana LGTM Distributed stack

## Deployment Order

The components should be deployed in the following order:

1. Install cert-manager
2. Create namespaces (otelbrot, monitoring)
3. Create Honeycomb API key secret
4. Install OpenTelemetry Operator
5. Install LGTM Distributed stack in the monitoring namespace
6. Install OpenTelemetry Cluster Collector
7. Deploy OpenTelemetry App Collector and instrumentation
8. Deploy Otelbrot application

This can be done with the Makefile command:

```
make helm-deploy
```

Or step by step:

```
make install-cert-manager
make helm-install-namespaces
make create-honeycomb-secret
make helm-install-otel-operator
make helm-install-lgtm-distributed
make helm-install-otel-cluster-collector
make deploy-otel-app-collector
make helm-install-otelbrot-app
```

## Namespaces

- **otelbrot**: Contains the application components and application-specific collectors
- **monitoring**: Contains the LGTM stack (Loki, Grafana, Tempo, Mimir)
- **opentelemetry-operator-system**: Contains the OpenTelemetry Operator

## Cleanup

To clean up all resources:

```
make helm-cleanup-all
```

Or to clean up just the application and collectors:

```
make helm-cleanup
```
