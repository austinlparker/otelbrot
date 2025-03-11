# Deprecated Kubernetes Manifests

**NOTE:** These Kubernetes manifests are deprecated and kept only for reference. 

## Migrated to Helm Charts

All deployment functionality has been migrated to Helm charts located in the `helm-charts/` directory. 
Please use the Helm-based deployment method instead:

```bash
make helm-deploy
```

See the project root README.md and the helm-charts/README.md for more information.

## Legacy Manifest List

These manifests are no longer actively maintained:

- `namespace.yaml`: Creates namespaces (replaced by namespaces Helm chart)
- `redis.yaml`: Redis deployment (included in otelbrot-app Helm chart)
- `rbac.yaml`: RBAC rules (included in Helm charts)
- `frontend.yaml`: Frontend deployment (included in otelbrot-app Helm chart)
- `go-worker.yaml`: Go worker deployment (included in otelbrot-app Helm chart)
- `orchestrator.yaml`: Orchestrator deployment (included in otelbrot-app Helm chart)
- `otel-agent-orchestrator-config.yaml`: OpenTelemetry agent config (included in Helm charts)
- `otel-configmap.yaml`: OpenTelemetry config (included in Helm charts)
- `opentelemetry.yaml`: OpenTelemetry collector (replaced by Helm-managed operators)
- `go-worker-otel-config.yaml`: Go worker OpenTelemetry config (included in Helm charts)