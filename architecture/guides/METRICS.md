# Metrics

## Endpoints and config

- **Prometheus** scrape format: **`/q/metrics`**, shared Micrometer settings in
  [`config/src/main/resources/metrics.properties`](https://github.com/get-forge/forge-platform/blob/main/config/src/main/resources/metrics.properties)
  (`quarkus.micrometer.export.prometheus.*`, HTTP/JVM/system binders, ignore patterns for
  health/metrics where configured).
- **Agroal** (datasource) metrics: `quarkus.datasource.metrics.enabled` in
  [`config/src/main/resources/database.properties`](https://github.com/get-forge/forge-platform/blob/main/config/src/main/resources/database.properties)
  (or module overrides).

## forge-kit (forge-metrics)

**Package:** `io.forge.kit.metrics` (from **get-forge/forge-kit**). Annotations and interceptors include:

- `@ServiceMetrics` + a `MetricsRecorder` implementation (e.g. `AuthMetricsRecorder`,
  `ActorMetricsRecorder`, `DocumentMetricsRecorder`, `NotificationMetricsRecorder`) on
  application services.
- `@DatabaseMetrics` / `@CircuitBreakerMetrics` on **repository** types (e.g.
  `ActorRepository`, `DocumentRepository`, `NotificationRepository`, related persistence types).
- **Rate limiting**: `RateLimitingFilter` (`libs/security`) uses
  `@ServiceMetrics(ThrottleMetricsRecorder.class)`; metrics use names such as
  `rate.limit.requests`, `rate.limit.violations`, `rate.limit.utilization`,
  `rate.limit.failures` (see `ThrottleMetricsRecorder` in forge-kit).

Repository: [forge-impl/forge-metrics](https://github.com/get-forge/forge-kit/tree/main/forge-impl/forge-metrics).

## Dashboards (local)

Pre-provisioned JSON under
[`.grafana/provisioning/dashboards/`](https://github.com/get-forge/forge-platform/tree/main/.grafana/provisioning/dashboards)
and helper scripts under
[`scripts/metrics/`](https://github.com/get-forge/forge-platform/tree/main/scripts/metrics).
Use with local Prometheus/Grafana (see
[`.grafana/README.md`](https://github.com/get-forge/forge-platform/blob/main/.grafana/README.md)).

## Security and network

Application **JWT** is **not** required for `/q/metrics` in default shared properties.
**Protecting** scrape targets is an **operational** concern (private subnets, security groups,
who can reach the ALB, optional internal-only listeners, or auth in front of Prometheus). The
public **WAF** in `infra` applies host/rate/geo style rules - it is **not** a dedicated
"block `/q/metrics`" path rule unless you add one.

## Exports

**Amazon CloudWatch** as a Micrometer registry is **not** in the current Maven dependencies.
