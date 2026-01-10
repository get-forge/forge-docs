# Metrics Usage Guide

This guide explains how to use the metrics infrastructure in the Bravo application.

## Overview

The application uses **Micrometer** for metrics collection, exposing metrics in **Prometheus** format at the
`/q/metrics` endpoint. Metrics are automatically scraped by Prometheus and can be visualized in Grafana.

## Available Metrics

### Automatic Metrics (Built-in)

The following metrics are automatically collected by Quarkus/Micrometer:

- **HTTP Server Metrics**: Request rate, latency, status codes (`http.server.requests`)
- **JVM Metrics**: Heap usage, GC, threads (`jvm.*`)
- **System Metrics**: CPU, memory, disk (`system.*`)
- **Process Metrics**: Uptime (`process.*`)
- **Database Metrics**: Agroal connection pool stats (enabled via `quarkus.datasource.metrics.enabled=true`)

### Rate Limiting Metrics

Rate limiting metrics are automatically collected by the `RateLimitingFilter` for all requests. These metrics provide:

1. **Violation Tracking**: Specific IP addresses or user IDs that exceed rate limits
2. **Generic Overview**: Overall rate limit utilization to assess if limits are reasonable

**Available Metrics:**

- `rate.limit.requests`: Counter tracking allowed and blocked requests
  - Tags: `key_type` (user, service, ip, auth), `status` (allowed, blocked)
  - Use for: Overall rate limiting activity

- `rate.limit.violations`: Counter tracking specific violations
  - Tags: `key_type` (user, service, ip, auth), `identifier` (username, serviceId, IP address)
  - Use for: Identifying specific entities that are hitting limits (alerting on breaches)

- `rate.limit.utilization`: Distribution summary tracking utilization percentage
  - Tags: `key_type` (user, service, ip, auth)
  - Use for: Understanding if limits are reasonable or being approached (0-100%)

- `rate.limit.failures`: Counter tracking rate limiting mechanism failures
  - Tags: `exception_type` (exception class name)
  - Use for: Detecting when the rate limiting system itself fails (e.g., Redis connection issues)

**Example Prometheus Queries:**

```promql
# Count of rate limit violations by type
sum(rate(rate_limit_violations_total[5m])) by (key_type)

# Specific IPs or users hitting limits
sum(rate(rate_limit_violations_total[5m])) by (identifier)

# Average utilization by key type (are limits reasonable?)
avg(rate_limit_utilization) by (key_type)

# Requests blocked vs allowed
sum(rate(rate_limit_requests_total[5m])) by (status)

# Rate limiting system failures
sum(rate(rate_limit_failures_total[5m])) by (exception_type)
```

**Grafana Dashboard Recommendations:**

- **Violations Panel**: Show `rate_limit_violations_total` grouped by `identifier` to identify problematic IPs/users
- **Utilization Panel**: Show `rate_limit_utilization` as a gauge or time series to see if limits are being approached
- **Overview Panel**: Show `rate_limit_requests_total` with `status=blocked` vs `status=allowed` for general health

### HTTP Error Rate Visualization

Error rates by endpoint are **derived** from HTTP status code metrics rather than being a separate explicit
metric. This approach is standard practice and provides the same operational value.

**Available in Grafana Dashboard:**

The "Quarkus HTTP Metrics" dashboard includes two error rate visualizations:

1. **Error Rate Table**: Shows current error rate percentage per endpoint
   - Color-coded: Green (<1%), Yellow (1-5%), Red (>5%)
   - Sorted by highest error rate first
   - Useful for quick identification of problematic endpoints

2. **Error Rate Heatmap**: Shows error rate trends over time by endpoint
   - Y-axis: Endpoints (sorted by name)
   - X-axis: Time
   - Color intensity: Error rate percentage (green → yellow → orange → red)
   - Perfect for spotting patterns: "Which endpoints have been failing this week?"

**Prometheus Queries:**

```promql
# Error rate percentage by endpoint (current)
sum(rate(http_server_requests_seconds_count{status=~"4..|5.."}[5m])) by (uri) 
/ 
sum(rate(http_server_requests_seconds_count[5m])) by (uri) * 100

# Error count by endpoint and status code
sum(rate(http_server_requests_seconds_count{status=~"4..|5.."}[5m])) by (uri, status)

# Total error rate across all endpoints
sum(rate(http_server_requests_seconds_count{status=~"4..|5.."}[5m])) 
/ 
sum(rate(http_server_requests_seconds_count[5m])) * 100
```

**Why Not an Explicit Metric?**

- **No code changes needed**: Uses existing HTTP metrics automatically collected
- **Lower overhead**: No additional metric collection in application code
- **Standard practice**: Most observability stacks derive error rates from HTTP status codes
- **Same operational value**: Provides identical insights to an explicit metric

If you need more granular error categorization (e.g., "validation errors" vs "database errors"), you could
add explicit metrics, but for general "is this endpoint failing?" visibility, derived metrics are
sufficient.

### Database Connection Pool Metrics

Quarkus automatically provides **Agroal connection pool metrics** when enabled. Quarkus uses Agroal as its
default connection pool (not HikariCP). These metrics are essential for monitoring database connection
health and identifying connection pool exhaustion issues.

**Configuration:**

Enable in `database.properties`:
```properties
quarkus.datasource.metrics.enabled=true
```

**Available Metrics:**

All metrics are prefixed with `agroal_` and include:

**Connection Pool Status:**
- `agroal_active_count` - Current number of active connections in use
- `agroal_available_count` - Current number of idle connections available
- `agroal_awaiting_count` - Current number of threads waiting to acquire a connection
- `agroal_max_used_count` - Maximum number of connections active simultaneously

**Connection Lifecycle:**
- `agroal_acquire_count_total` - Total number of successful connection acquisitions
- `agroal_creation_count_total` - Total number of connections created
- `agroal_destroy_count_total` - Total number of connections destroyed
- `agroal_reap_count_total` - Total number of connections removed for being idle
- `agroal_invalid_count_total` - Total number of invalid connections removed
- `agroal_flush_count_total` - Total number of connections flushed from pool

**Performance Metrics:**
- `agroal_blocking_time_average_milliseconds` - Average time waiting to acquire a connection
- `agroal_blocking_time_max_milliseconds` - Maximum time waiting to acquire a connection
- `agroal_blocking_time_total_milliseconds` - Total time spent waiting for connections
- `agroal_creation_time_average_milliseconds` - Average time to create a connection
- `agroal_creation_time_max_milliseconds` - Maximum time to create a connection
- `agroal_creation_time_total_milliseconds` - Total time spent creating connections

**Leak Detection:**
- `agroal_leak_detection_count_total` - Number of connection leaks detected

**Prometheus Queries:**

```promql
# Current active connections
agroal_active_count{datasource="default"}

# Current available (idle) connections
agroal_available_count{datasource="default"}

# Connection pool utilization (requires knowing max pool size from config)
# Example: if max-size=20
(agroal_active_count{datasource="default"} / 20) * 100

# Average connection acquisition time
agroal_blocking_time_average_milliseconds{datasource="default"}

# Maximum connection acquisition time
agroal_blocking_time_max_milliseconds{datasource="default"}

# Pending connection requests (indicates pool exhaustion)
agroal_awaiting_count{datasource="default"}

# Connection acquisition rate
rate(agroal_acquire_count_total{datasource="default"}[5m])

# Connection leak detection
rate(agroal_leak_detection_count_total{datasource="default"}[5m])
```

**Grafana Dashboard Recommendations:**

- **Connection Pool Status**: Gauge showing active/available/max connections
- **Connection Utilization**: Percentage gauge (active/max pool size)
- **Connection Acquisition Time**: Time series showing average and max blocking time
- **Connection Creation Time**: Time series showing average and max creation time
- **Pending Connections**: Alert when > 0 (indicates pool exhaustion)
- **Connection Leaks**: Counter showing leak detection rate

**Alerting Thresholds:**

- **Warning**: `agroal_awaiting_count{datasource="default"} > 0` (threads waiting for connections)
- **Warning**: `agroal_blocking_time_average_milliseconds{datasource="default"} > 100` (avg acquisition time > 100ms)
- **Critical**: `agroal_awaiting_count{datasource="default"} > 5` (multiple threads waiting)
- **Critical**: `rate(agroal_leak_detection_count_total{datasource="default"}[5m]) > 0` (connection leaks detected)

### Custom Metrics

Use `ApplicationMetrics` from the `common` library to record custom business metrics:

```java
import tech.eagledrive.metrics.ApplicationMetrics;
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
- `entity`: Entity type (`candidate`)
- `key_type`: Rate limit key type (`user`, `service`, `ip`, `auth`)
- `identifier`: Rate limit identifier (username, serviceId, IP address)

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

