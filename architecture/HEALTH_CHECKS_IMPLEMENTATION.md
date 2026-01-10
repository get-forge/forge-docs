# Enhanced Health Checks Implementation Plan

**Date:** 2025-01-27  
**Status:** In Progress  
**Related:** [ARCHITECTURAL_IMPROVEMENTS.md](./ARCHITECTURAL_IMPROVEMENTS.md#12-enhanced-health-checks)

## Executive Summary

This document outlines the implementation steps for Enhanced Health Checks across all services. The
implementation is **straightforward** (~1-2 days) and follows Quarkus SmallRye Health best practices.

## Health Check Strategy

### Which Services Need Health Checks?

**✅ CRITICAL - Implement Readiness Checks:**
- **PostgreSQL** (candidate-service) - Primary database, must be available
- **DynamoDB** (document-service) - Primary data store
- **Cognito** (auth-service) - Authentication required for all operations, all Cognito usage is via auth-service
- **S3** (document-service) - Required for document uploads

**⚠️ OPTIONAL - Consider for Production:**
- **SSM Parameter Store** - Used for secrets, but failures are typically caught at startup
- **IAM** - Used for service-to-service auth, but failures are immediate and fatal

**❌ NOT NEEDED:**
- **TextKernel API** - External service, handled by circuit breakers
- **Service-to-service HTTP calls** - Handled by circuit breakers and retries

### Best Practices

1. **Readiness Checks** (`@Readiness`) - Check dependencies that must be available for the service to accept traffic
   - Use for: Databases, storage, authentication services
   - Purpose: ALB target group health checks, Kubernetes readiness probes
   - Should fail fast if dependency is unavailable

2. **Liveness Checks** (`@Liveness`) - Check if the application itself is running
   - Use for: Basic application health, memory/thread pool status
   - Purpose: Container orchestration restart decisions
   - Should be lightweight and never fail under normal conditions

3. **Startup Checks** (`@Startup`) - Check if application has finished starting
   - Use for: Initialization verification
   - Purpose: Kubernetes startup probes

## Implementation Approach

Instead of per-service concrete health check classes, we now use a shared library plus per-service registration classes.

### Shared health check library (`libs/health`)

The shared library provides reusable, dependency agnostic health check base classes:

- `PostgresHealthCheck` - checks database connectivity and optionally multiple tables using a `List<String>` of table names  
- `S3HealthCheck` - checks accessibility of one or more S3 buckets  
- `DynamoDbHealthCheck` - checks accessibility of one or more DynamoDB tables  
- `CognitoHealthCheck` - checks accessibility of a Cognito User Pool

Key characteristics:

- Base classes are abstract and are not CDI beans  
- They accept fully configured clients and identifiers (database name, table names, bucket names, user pool IDs) via constructors  
- They do not depend on environment or service specific configuration  
- They surface useful diagnostic data (database name, tables, buckets, user pool ID) in the `HealthCheckResponse`

### Per service registration pattern (`*ServiceHealthChecks`)

Each service now has a single registration class that wires dependencies and exposes health checks via producer methods:

- `CandidateServiceHealthChecks`
  - Produces a Postgres readiness check using an anonymous subclass of `PostgresHealthCheck`
  - Checks the `bravo` database and the `candidates` table

- `DocumentServiceHealthChecks`
  - Produces an S3 readiness check using `S3HealthCheck` for:
    - `bravo-candidate-resumes`
    - `bravo-client-jobs`
  - Produces a DynamoDB readiness check using `DynamoDbHealthCheck` for:
    - `RESUMES`
    - `JOBS`

- `AuthServiceHealthChecks`
  - Produces two Cognito readiness checks using `CognitoHealthCheck` for:
    - candidate user pool
    - service user pool

Pattern details:

- Each health check is produced by a `@Produces @Readiness @ApplicationScoped` method  
- Dependencies such as `EntityManager`, `S3Client`, `DynamoDbClient`, and `CognitoIdentityProviderClient`
  are injected as method parameters or fields  
- Producer methods return anonymous subclasses of the base health check types, centralising all
  registrations in a single `*ServiceHealthChecks` class per service  
- Quarkus built in datasource health check is disabled and replaced with the custom Postgres check  

### Step 6: Configure Health Check Endpoints

Health check endpoints are automatically available at:
- `/q/health/live` - Liveness check (all services)
- `/q/health/ready` - Readiness check (aggregates all `@Readiness` checks)
- `/q/health/started` - Startup check (if any `@Startup` checks exist)

**Optional:** Configure health check UI in `application.properties`:

```properties
# Enable health check UI (optional, for development)
quarkus.smallrye-health.ui.enable=true
```

Access at: `http://localhost:PORT/q/health-ui`

### Step 7: Test Health Checks

1. **Start all services** and verify health endpoints:
   ```bash
   curl http://localhost:8080/q/health/ready
   ```

2. **Expected response (all up):**
   ```json
   {
     "status": "UP",
     "checks": [
       {
         "name": "PostgreSQL",
         "status": "UP",
         "data": {
           "database": "bravo"
         }
       },
       {
         "name": "Cognito",
         "status": "UP",
         "data": {
           "userPoolId": "...",
           "status": "Active"
         }
       }
     ]
   }
   ```

3. **Test failure scenarios:**
   - Stop PostgreSQL container → DatabaseHealthCheck should return `DOWN`
   - Stop LocalStack → DynamoDB/S3 health checks should return `DOWN`
   - Invalid Cognito credentials → CognitoHealthCheck should return `DOWN`

### Step 8: Configure ALB Target Group (Production)

In AWS, configure Application Load Balancer target group health checks:

- **Health check path:** `/q/health/ready`
- **Health check protocol:** HTTP
- **Health check port:** 8080 (or your service port)
- **Healthy threshold:** 2
- **Unhealthy threshold:** 3
- **Timeout:** 5 seconds
- **Interval:** 30 seconds

## AWS Region Considerations

### Cognito Health Checks (us-west-2)

The Cognito health check uses `DescribeUserPool`, which:
- ✅ Works with us-west-2 (your configured region)
- ✅ Fails fast if region is unavailable
- ✅ Does not require us-east-1

**No changes needed** - your existing `CognitoClientProducer` already configures the region correctly.

### LocalStack vs AWS Production

Health checks work with both:
- **LocalStack (local):** Uses endpoint override from `aws.dynamodb.endpoint`, `aws.s3.endpoint`
- **AWS Production:** Uses default AWS endpoints in us-west-2

The health check implementation is environment-agnostic - it uses the same clients configured by your producers.

## Error Handling Best Practices

1. **Timeout Protection:** Health checks should complete within 2-3 seconds
   - Use `@Timeout` annotation if needed (though health checks should be fast)
   - Consider async health checks for slow dependencies (not needed here)

2. **Exception Handling:** Catch specific exceptions:
   - AWS SDK exceptions (`DynamoDbException`, `S3Exception`, `CognitoIdentityProviderException`)
   - Generic exceptions as fallback

3. **Error Messages:** Include actionable information:
   - Service name
   - Resource identifier (table name, bucket name, user pool ID)
   - Error message from AWS SDK

## Performance Considerations

1. **Lightweight Operations:**
   - Use `SELECT 1` for database (not a full query)
   - Use `describeTable` for DynamoDB (metadata operation, not a scan)
   - Use `headBucket` for S3 (metadata operation, not a full list)
   - Use `describeUserPool` for Cognito (metadata operation, not user lookup)

2. **Caching:** Health checks are called frequently (every 30 seconds by ALB)
   - Keep operations fast (< 1 second)
   - Avoid caching health check results (always verify actual connectivity)

## Verification Checklist

- [x] Custom Postgres health check implemented in candidate-service (checks `bravo` and `candidates` table)
- [x] Custom S3 health check implemented in document-service (checks resume and job buckets)
- [x] Custom DynamoDB health check implemented in document-service (checks `RESUMES` and `JOBS` tables)
- [x] Custom Cognito health checks implemented in auth-service (candidate and service user pools)
- [x] All health checks return `UP` when dependencies are available
- [x] All health checks return `DOWN` when dependencies are unavailable
- [x] `/q/health/ready` endpoint returns aggregated status
- [ ] Health check responses include useful diagnostic data
- [x] Tested with LocalStack (local development)
- [x] Tested with AWS services (production-like environment)

## Next Steps

After implementation:
1. Configure ALB target group health checks (production)
2. Set up CloudWatch alarms for health check failures
3. Document health check endpoints in API documentation
4. Consider adding health check metrics (track health check duration, failures)

### Future: Bravo application status page

We want a Bravo-wide status page (similar to the Quarkus health UI or AWS service status pages) that
shows the status and uptime of all application services.

Potential approaches:

- **External status-page SaaS**
  - Use a managed status-page provider (for example, Atlassian Statuspage, Better Uptime, UptimeRobot)
    and configure each component to poll the services' `/q/health/ready` (or a thin wrapper endpoint).
  - Pros: No extra code to maintain, built-in incident history and notifications.
  - Cons: Additional external dependency and cost, limited customization.

- **Quarkus “status gateway” service**
  - Add a small Quarkus service that periodically calls each service’s `/q/health/ready` and exposes:
    - An aggregated JSON endpoint (for example, `/status/api`) summarising all services.
    - A simple HTML/Qute based UI (for example, `/status`) that looks like an internal Bravo status dashboard.
  - Pros: Fully under our control, reuses existing health endpoints and Quarkus stack.
  - Cons: We own availability and maintenance of this extra service.

- **Observability driven internal dashboard**
  - Use Prometheus and Grafana to build an internal "ops status" view that treats health endpoints as
    blackbox checks and shows service availability and uptime graphs.
  - Pros: Rich metrics and history, no extra application code.
  - Cons: More suitable for internal SRE/engineering than a polished, user facing status page.

Decision and implementation for the status page are intentionally deferred; this section captures the preferred options for future work.

## References

- [Quarkus SmallRye Health Guide](https://quarkus.io/guides/smallrye-health)
- [MicroProfile Health Specification](https://microprofile.io/project/eclipse/microprofile-health)
- [AWS SDK for Java v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)

