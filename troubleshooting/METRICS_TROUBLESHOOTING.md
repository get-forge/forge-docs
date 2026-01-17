# Metrics Troubleshooting Guide

## Issue: No Data Showing in Grafana Dashboards

If you're not seeing data in Grafana dashboards, follow these steps:

## Step 1: Verify Metrics Endpoint is Accessible

Check if your services are exposing metrics:

```bash
# Check auth-service metrics endpoint
curl http://localhost:8100/q/metrics

# You should see Prometheus-formatted metrics output
# If you get 404, the metrics endpoint isn't enabled
```

## Step 2: Check Prometheus Targets

1. Open Prometheus UI: http://localhost:9090
2. Go to **Status → Targets**
3. Check if all services show as **UP** (green) or **DOWN** (red)

If targets are DOWN:
- Check the error message (usually "404 Not Found" or connection refused)
- Verify services are running
- Check that services are listening on the correct ports

## Step 3: Verify Services Are Running

```bash
# Check if services are running
task services:status

# Or check individual services
curl http://localhost:8100/q/health
```

## Step 4: Restart Services

If metrics endpoint returns 404, restart your services:

```bash
# Stop services
task services:stop

# Start services
task services:start

# Wait for services to fully start, then check metrics again
curl http://localhost:8100/q/metrics
```

## Step 5: Check Grafana Data Source

1. Open Grafana: http://localhost:3000
2. Login with `admin/admin`
3. Go to **Configuration → Data Sources**
4. Click on **Prometheus**
5. Click **Test** - should show "Data source is working"

## Step 6: View the Dashboard

1. In Grafana, go to **Dashboards → Browse**
2. Look for **"Forge Platform Metrics"**
3. If you don't see it, wait a few seconds (dashboards auto-load every 10 seconds)
4. Or manually refresh: **Configuration → Provisioning → Dashboards → Reload**

## Step 7: Verify Data in Prometheus

Before checking Grafana, verify Prometheus has data:

1. Go to http://localhost:9090
2. In the query box, try:
   - `http_server_requests_seconds_count` - Should show HTTP request metrics
   - `auth_attempts_total` - Should show authentication metrics (if implemented)
   - `up` - Should show which targets are up

## Common Issues

### Issue: "404 Not Found" on /q/metrics

**Cause**: Metrics endpoint not enabled or service needs restart

**Solution**:
1. Verify `metrics.properties` is in `quarkus.config.locations` (check `application.properties`)
2. Restart the service
3. Check service logs for Micrometer initialization errors

### Issue: Prometheus shows targets as DOWN

**Cause**: Services not accessible from Prometheus container

**Solution**:
- On Mac/Windows, Prometheus uses `host.docker.internal` to reach services
- Verify services are running on the expected ports
- Check firewall/network settings

### Issue: Dashboard shows "No data"

**Cause**: 
- No metrics being collected yet
- Time range doesn't include when data was collected
- Wrong metric names in queries

**Solution**:
1. Check Prometheus directly to see if metrics exist
2. Adjust time range in Grafana (top right corner)
3. Verify metric names match what's actually exposed

## Testing Metrics Collection

To test if metrics are working:

1. **Make some API calls**:
   ```bash
   # Login attempt
   curl -X POST http://localhost:8100/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"test","password":"test"}'
   ```

2. **Check metrics endpoint**:
   ```bash
   curl http://localhost:8100/q/metrics | grep http_server_requests
   ```

3. **Query in Prometheus**:
   - Go to http://localhost:9090
   - Query: `rate(http_server_requests_seconds_count[5m])`
   - Should show request rate data

4. **Check Grafana dashboard**:
   - Open "Forge Platform Metrics" dashboard
   - Should see HTTP request rate graph updating

## Next Steps

Once metrics are working:
- The dashboard will automatically show HTTP metrics (request rate, duration, status codes)
- Custom metrics (like `auth_attempts_total`) will appear once you add metric recording to your services
- See `docs/architecture/METRICS_USAGE.md` for how to add custom metrics

