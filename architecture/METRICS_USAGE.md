# Metrics Usage Guide

This guide explains how to use the metrics infrastructure in the Bravo application.

## Overview

The application uses **Micrometer** for metrics collection, exposing metrics in **Prometheus** format at the `/q/metrics` endpoint. Metrics are automatically scraped by Prometheus and can be visualized in Grafana.

## Available Metrics

### Automatic Metrics (Built-in)

The following metrics are automatically collected by Quarkus/Micrometer:

- **HTTP Server Metrics**: Request rate, latency, status codes (`http.server.requests`)
- **JVM Metrics**: Heap usage, GC, threads (`jvm.*`)
- **System Metrics**: CPU, memory, disk (`system.*`)
- **Process Metrics**: Uptime (`process.*`)
- **Database Metrics**: Connection pool stats (if using HikariCP)

### Custom Metrics

Use `ApplicationMetrics` from the `common` library to record custom business metrics:

```java
import tech.eagledrive.common.metrics.ApplicationMetrics;
import jakarta.inject.Inject;

@ApplicationScoped
public class MyService {
    @Inject
    ApplicationMetrics metrics;

    public void doSomething() {
        // Record authentication success
        metrics.recordAuthenticationSuccess("user");
        
        // Record authentication failure
        metrics.recordAuthenticationFailure("user", "invalid_credentials");
        
        // Record external API call duration
        long startTime = System.currentTimeMillis();
        // ... make API call ...
        long duration = System.currentTimeMillis() - startTime;
        metrics.recordExternalApiCall("textkernel", "parse-resume", duration);
        
        // Record database operation
        long dbStart = System.currentTimeMillis();
        // ... database operation ...
        long dbDuration = System.currentTimeMillis() - dbStart;
        metrics.recordDatabaseOperation("find", "candidate", dbDuration);
        
        // Record business event
        metrics.recordBusinessEvent("document.uploaded", "success");
    }
}
```

## Example: Adding Metrics to AuthService

```java
@ApplicationScoped
public class AuthService {
    @Inject
    ApplicationMetrics metrics;
    
    @Inject
    UserAuthenticationProvider userAuthenticationProvider;

    public AuthResponse login(final LoginRequest request) {
        final long startTime = System.currentTimeMillis();
        try {
            final AuthResponse result = userAuthenticationProvider.login(
                request.getUsername(), 
                request.getPassword()
            );
            
            if (result.success()) {
                metrics.recordAuthenticationSuccess("user");
            } else {
                metrics.recordAuthenticationFailure("user", 
                    result.errorMessage() != null ? "invalid_credentials" : "unknown");
            }
            
            return result;
        } catch (Exception e) {
            metrics.recordAuthenticationFailure("user", "exception");
            throw e;
        } finally {
            // HTTP metrics are automatically recorded by Quarkus
            // But you can add custom timing if needed
        }
    }
}
```

## Accessing Metrics

### Local Development

1. **Start Prometheus and Grafana**:
   ```bash
   task metrics-start
   # or individually:
   task prometheus-start
   task grafana-start
   ```

2. **Access Services**:
   - **Prometheus**: http://localhost:9090
   - **Grafana**: http://localhost:3000 (admin/admin)
   - **Metrics Endpoint**: http://localhost:8081/q/metrics (for auth-service)

3. **View Metrics in Prometheus**:
   - Go to http://localhost:9090
   - Use the query interface to explore metrics
   - Example queries:
     - `http_server_requests_total` - Total HTTP requests
     - `http_server_requests_seconds` - HTTP request duration
     - `auth_attempts_total` - Authentication attempts
     - `jvm_memory_used_bytes` - JVM memory usage

4. **View Dashboards in Grafana**:
   - Go to http://localhost:3000
   - Login with admin/admin
   - Prometheus datasource is automatically configured
   - Create custom dashboards or import pre-built ones

### Production (AWS)

In production, metrics will be exported to CloudWatch using `micrometer-registry-cloudwatch2`.

## Metric Naming Conventions

- Use dots (`.`) to separate words: `auth.attempts`, `database.operation.duration`
- Use lowercase: `auth.attempts` not `Auth.Attempts`
- Be descriptive: `external.api.call.duration` not `api.time`
- Use consistent tags across related metrics

## Tags

All metrics are automatically tagged with:
- `application`: Service name (e.g., `auth-service`)
- `version`: Application version

Custom metrics can add additional tags:
- `type`: Authentication type (`user`, `service`)
- `status`: Operation status (`success`, `failure`)
- `api`: External API name (`textkernel`, `cognito`)
- `operation`: Database operation (`find`, `save`, `delete`)
- `entity`: Entity type (`candidate`, `match`)

## Best Practices

1. **Don't Over-Metric**: Only track metrics that provide value for operations and debugging
2. **Use Histograms for Latency**: Prefer `Timer` over `Counter` for duration metrics
3. **Tag Wisely**: Too many tags can cause cardinality explosion
4. **Document Metrics**: Add descriptions to all custom metrics
5. **Monitor Cardinality**: High-cardinality metrics (many unique tag combinations) can impact performance

## Troubleshooting

### Metrics Not Appearing

1. Check that `metrics.properties` is in `quarkus.config.locations`
2. Verify Micrometer is enabled: `quarkus.micrometer.enabled=true`
3. Check service logs for Micrometer initialization errors
4. Verify Prometheus can reach the service (check `host.docker.internal` on Mac/Windows)

### Prometheus Not Scraping

1. Check Prometheus targets: http://localhost:9090/targets
2. Verify service ports match Prometheus config
3. Check service is running and `/q/metrics` endpoint is accessible
4. Review Prometheus logs: `docker logs prometheus`

### Grafana Not Showing Data

1. Verify Prometheus datasource is configured (should be automatic)
2. Check datasource connection: Grafana → Configuration → Data Sources
3. Verify Prometheus is running and accessible from Grafana container
4. Check query syntax in Grafana panels

## References

- [Quarkus Micrometer Guide](https://quarkus.io/guides/micrometer)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/)

