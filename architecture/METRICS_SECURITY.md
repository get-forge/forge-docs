# Metrics Endpoint Security

**Last Updated:** 2025-01-17  
**Status:** ⚠️ Security Issue Identified

## Current State

The `/q/metrics` endpoint is **publicly accessible** without authentication. This is a security vulnerability that exposes:

- Business metrics (authentication attempts, request patterns)
- System internals (JVM memory, GC, threads, CPU usage)
- Performance characteristics (request rates, latencies, error rates)
- Application topology (service names, endpoints)

## Security Configuration

### Option 1: Require Authentication (Recommended)

Require JWT authentication for the metrics endpoint. This works with your existing `TokenAuthenticationFilter`:

```properties
# config/src/main/resources/metrics.properties
quarkus.http.auth.permission.metrics.paths=/q/metrics
quarkus.http.auth.permission.metrics.policy=authenticated
```

**Pros:**
- Uses existing JWT authentication infrastructure
- Service tokens can be used for Prometheus scraping
- Consistent with application security model

**Cons:**
- Prometheus scraping needs to be configured with service tokens
- Requires updating Prometheus configuration

**Prometheus Configuration Update:**
```yaml
# .prometheus/prometheus.yml
scrape_configs:
  - job_name: 'auth-service'
    metrics_path: '/q/metrics'
    static_configs:
      - targets: ['host.docker.internal:8100']
        labels:
          service: 'auth-service'
          application: 'bravo'
    # Add authentication for scraping
    bearer_token: '${SERVICE_TOKEN}'  # Or use basic_auth
```

### Option 2: Separate Management Port (Enhanced Security)

Expose management endpoints on a separate port with authentication:

```properties
# Expose management endpoints on separate port
quarkus.management.enabled=true
quarkus.management.port=9000
quarkus.management.auth.enabled=true

# Move metrics to management port
quarkus.micrometer.export.prometheus.path=/metrics
```

**Pros:**
- Complete isolation of management endpoints
- Can use different authentication mechanism
- Easier to firewall/restrict access

**Cons:**
- Requires additional port configuration
- More complex deployment
- Prometheus needs to scrape different ports

### Option 3: Network-Level Restrictions (Defense in Depth)

Restrict access at the network/firewall level:

- Only allow Prometheus server IPs to access `/q/metrics`
- Use AWS Security Groups / Network ACLs in production
- Use reverse proxy (nginx, ALB) with IP whitelisting

**Pros:**
- Defense in depth
- No application code changes
- Works with existing Prometheus setup

**Cons:**
- Not sufficient alone (should be combined with authentication)
- Requires network infrastructure changes
- Harder to manage in dynamic environments

## Recommended Approach

**For Development:**
- Use Option 1 (authentication) with service tokens
- Update Prometheus configuration to use service tokens
- Document service token usage for Prometheus scraping

**For Production:**
- Use Option 1 (authentication) + Option 3 (network restrictions)
- Consider Option 2 (separate management port) for enhanced security
- Use TLS/HTTPS for all metrics endpoints
- Rotate service tokens regularly

## Implementation Steps

1. **Enable Authentication** (Option 1)
   - Add security properties to `metrics.properties`
   - Test that unauthenticated requests return 401

2. **Update Prometheus Configuration**
   - Configure Prometheus to use service tokens
   - Update `prometheus-config.sh` to include authentication
   - Test scraping works with authentication

3. **Update Documentation**
   - Document service token usage for Prometheus
   - Update troubleshooting guides
   - Add security considerations to metrics documentation

4. **Production Hardening**
   - Add network-level restrictions
   - Enable TLS/HTTPS
   - Consider separate management port

## Testing

After implementing authentication:

```bash
# Should return 401 Unauthorized
curl http://localhost:8100/q/metrics

# Should return metrics with valid token
curl -H "Authorization: Bearer $SERVICE_TOKEN" http://localhost:8100/q/metrics
```

## References

- [Quarkus Management Interface Security](https://quarkus.io/guides/management-interface-reference)
- [Prometheus Authentication](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#scrape_config)
- [Quarkus Security Guide](https://quarkus.io/guides/security)

