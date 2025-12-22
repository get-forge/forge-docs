# Architectural Improvements & Production Readiness

**Date:** 2025-01-27  
**Status:** Analysis & Recommendations  
**Context:** High-level architectural improvements for enterprise, production-grade, high-scale system

## Executive Summary

This document identifies high-impact architectural improvements aligned with enterprise best practices, 12-factor app principles, and production-grade system requirements. Recommendations are prioritized by impact and effort, focusing on boilerplate and productionization rather than feature development.

## Current State Assessment

### ✅ Strengths (Already Implemented)

1. **Security Architecture**
   - Zero-trust JWT-based authentication
   - Stateless frontend and backend
   - Service-to-service authentication
   - Secrets management (SmallRye Config Crypto + AWS SSM)
   - Network-level security for management endpoints (WAF + Security Groups + VPC isolation)

2. **Fault Tolerance**
   - Circuit breakers, retries, timeouts (MicroProfile Fault Tolerance)
   - Implemented in repositories (DB, DynamoDB, S3 operations)

3. **Observability Foundation**
   - Distributed logging (OpenTelemetry + Jaeger)
   - Distributed tracing enabled
   - Health check endpoints (SmallRye Health)

4. **Code Quality**
   - Static analysis (Checkstyle, PMD, SpotBugs, OWASP Dependency Check)
   - Git hooks (Lefthook)
   - Commit message conventions
   - Clean architecture with ADRs

5. **CI/CD**
   - GitHub Actions workflows
   - Automated testing
   - Dependency management (Renovate)

---

## Priority 1: High Impact, Production Blockers

### 1.1 Metrics Implementation (CRITICAL)

**Status:** ~95% Complete  
**Last Updated:** 2025-01-27

**Current State:** 
- ✅ Micrometer configured and Prometheus registry enabled
- ✅ Metrics endpoint working (`/q/metrics`)
- ✅ Prometheus and Grafana infrastructure set up
- ✅ Five dashboards created (HTTP, User, JVM, Rate Limiting, Infrastructure metrics)
- ✅ Authentication metrics implemented in AuthService
- ✅ Rate limiting metrics fully implemented with comprehensive dashboard
- ✅ External API call metrics (TextKernel) implemented in document-service and match-service
- ✅ Database operation metrics implemented via `@DatabaseMetrics` interceptor pattern
- ✅ Database connection pool metrics (Agroal) enabled and visualized
- ✅️ Circuit breaker state transition metrics captured and displayed
- ⏸️ CloudWatch integration deferred to production infrastructure work

**Impact:** Metrics infrastructure is production-ready with comprehensive coverage of application operations, external APIs, database operations, and connection pools. All necessary application-level metrics are implemented and operational.

**Completed:**

1. **✅ Micrometer Registry Configured**
   - `metrics.properties` configured with all required settings
   - Prometheus exporter enabled
   - HTTP, JVM, and system metrics enabled
   - `quarkus-micrometer-registry-prometheus` dependency added

2. **✅ Infrastructure Setup**
   - Prometheus scraping all services
   - Grafana dashboards:
     - Quarkus HTTP Metrics (request rate, duration, status codes, error rate heatmap by endpoint)
     - Bravo User Metrics (authentication attempts)
     - Quarkus JVM Metrics (per-service memory, GC, threads)
     - Bravo Throttle Metrics (rate limiting requests, violations, utilization)
     - Bravo Infrastructure Metrics (TextKernel API, database operation duration, database connection pool metrics)

3. **✅ Metrics Infrastructure**
   - Authentication metrics implemented in `AuthService`
   - Rate limiting metrics fully implemented via `ThrottleMetricsHandler` with comprehensive tracking
   - External API call metrics (TextKernel) implemented in `document-service` and `match-service`, tested in Grafana
   - Database operation metrics implemented via `@DatabaseMetrics` annotation and `DatabaseMetricsInterceptor` for automatic timing collection
   - Database connection pool metrics (Agroal) automatically exposed and visualized in Infrastructure dashboard
   - Error rate by endpoint visualized via heatmap and table panels (derived from HTTP status codes)
   - Circuit breaker state transitions implemented with `@CircuitBreakerMetrics` for automatic state change recording

**Decision - Business Event Metrics:**
Additional business event metrics beyond the current implementation are **not required**. The existing metrics (HTTP, authentication, rate limiting, external API calls, database operations, and connection pools) provide sufficient operational visibility. Business events are adequately captured through HTTP request metrics and application logs.

**Decision - AWS Service Metrics (Cognito, S3, DynamoDB):**
Metrics for AWS-managed services are **not recommended** because:
- AWS already provides CloudWatch metrics for their services
- Health checks (already implemented) catch availability issues
- Application-level metrics (HTTP latency, error rates) already reflect AWS service performance
- AWS outages are well-publicized and affect all customers
- Adds code complexity with limited operational value

Health checks are sufficient for AWS services - they provide the necessary dependency status for load balancers and operators without the overhead of custom metrics.

**Deferred Work:**

1. **CloudWatch Integration** (deferred to production infrastructure work)
   - Add `micrometer-registry-cloudwatch2` dependency
   - Configure for production environment
   - Document metrics export strategy
   - Will be addressed as part of production infrastructure setup

**Security Decision - Metrics Endpoint Protection:**

Application-level authentication for `/q/metrics` and `/q/health` endpoints is **not required** given the AWS infrastructure security layers:

- **AWS WAF**: Programmatically configured to block `/q/metrics` and `/q/health` from external/public IPs, allowing only known monitoring system IPs (Prometheus, CloudWatch, etc.)
- **Security Groups**: ECS tasks restricted to allow traffic only from ALB security groups and monitoring system security groups
- **VPC Isolation**: Containers deployed in private subnets with no public IPs, NAT Gateway for outbound-only internet access
- **ALB**: SSL termination and path-based routing with internal ALB for metrics/health endpoints if needed

This defense-in-depth approach (WAF → ALB → Security Groups → VPC) provides sufficient protection without requiring application-level authentication, simplifying Prometheus scraping and reducing operational complexity.

**See:** `docs/architecture/METRICS_IMPLEMENTATION_STATUS.md` for detailed status  
**See:** `docs/architecture/METRICS_SECURITY.md` for security analysis (superseded by infrastructure-based approach)

**Effort Remaining:** CloudWatch integration deferred to production infrastructure work  
**Value:** Critical for production operations - application-level metrics complete and production-ready

---

### 1.2 Enhanced Health Checks

**Status:** ~90% Complete  
**Last Updated:** 2025-01-27

**Current State:** Custom readiness checks are implemented via a shared health library and per service registration classes. All critical backend dependencies (PostgreSQL, DynamoDB, S3, Cognito) now have explicit readiness checks where they are actually used.

**Impact:** Services can now fail fast when dependencies are unavailable, and `/q/health/ready` exposes a clear view of dependency status for load balancers and operators.

**Completed:**

1. **Shared health check library (`libs/health`)**
   - Introduced reusable abstract health check base classes:
     - `PostgresHealthCheck` (supports checking multiple tables)
     - `S3HealthCheck` (supports checking multiple buckets)
     - `DynamoDbHealthCheck` (supports checking multiple tables)
     - `CognitoHealthCheck` (checks Cognito User Pool accessibility)
   - Base classes are dependency agnostic and accept fully configured clients and identifiers.

2. **Per service health check registration pattern**
   - Each service uses a single `*ServiceHealthChecks` class with `@Produces @Readiness @ApplicationScoped` methods that return anonymous subclasses of the shared base checks.
   - `candidate-service`
     - Custom Postgres readiness check using `PostgresHealthCheck` for `bravo` and the `candidates` table.
   - `document-service`
     - Custom S3 readiness check using `S3HealthCheck` for `bravo-candidate-resumes` and `bravo-client-jobs`.
     - Custom DynamoDB readiness check using `DynamoDbHealthCheck` for `RESUMES` and `JOBS`.
   - `match-service`
     - Custom DynamoDB readiness check using `DynamoDbHealthCheck` for match related tables.
   - `auth-service`
     - Two custom Cognito readiness checks using `CognitoHealthCheck` for the candidate and service user pools.
   - Quarkus built-in datasource health check is disabled in favor of the custom Postgres health check.

3. **Documentation**
   - `HEALTH_CHECKS_IMPLEMENTATION.md` updated to describe the shared library pattern, per-service registration classes, and verification checklist.

**Remaining Work:**

1. **Liveness and startup checks (optional but recommended)**
   - Keep existing basic liveness checks.
   - Consider adding lightweight `@Liveness` checks for JVM/memory/thread pool where helpful.
   - Consider `@Startup` checks if future startup-time work (for example migrations or warmups) needs explicit validation.

2. **Operational integration**
   - Confirm all production ALB target groups use `/q/health/ready` for health checks.
   - Wire health check failures into alerting (CloudWatch alarms, PagerDuty/Slack, etc).

3. **Future enhancement - Bravo status page**
   - Design and implement a Bravo wide application status page that aggregates service health:
     - Option A: External status page SaaS (for example, Atlassian Statuspage, Better Uptime) polling `/q/health/ready`.
     - Option B: Small Quarkus “status gateway” service aggregating health endpoints and serving JSON + HTML/Qute dashboard.
     - Option C: Prometheus/Grafana driven internal “ops status” dashboard based on health and uptime metrics.
   - Actual choice and implementation are deferred; options are captured in `HEALTH_CHECKS_IMPLEMENTATION.md` for future work.

---

### 1.3 Rate Limiting

**Status:** ~95% Complete  
**Last Updated:** 2025-01-27

**Current State:** Application-level rate limiting is fully implemented using Bucket4j with per-user, per-service, and per-IP limits. The system includes comprehensive metrics collection and Grafana dashboards for monitoring. Rate limiting runs before authentication to protect all endpoints, including public authentication endpoints.

**Impact:** Services are now protected against abuse, DDoS, and accidental traffic spikes. Rate limit violations and utilization are tracked with detailed metrics for operational visibility.

**Completed:**

1. **Application-Level Rate Limiting Infrastructure**
   - Bucket4j-based rate limiter implementation with token bucket algorithm
   - `RateLimitingFilter` using `@ServerRequestFilter` with priority 1 (before authentication)
   - Conditional bean production - rate limiting only active when configuration is present
   - Supports gradual rollout per service via configuration

2. **Multi-Level Rate Limiting**
   - Per-user rate limits (extracted from JWT user tokens)
   - Per-service rate limits (extracted from JWT service tokens via `custom:service_id` claim)
   - Per-IP rate limits (for unauthenticated requests)
   - Special handling for malformed tokens (`auth:unidentified` key to prevent IP fallback exploitation)

3. **Configuration System**
   - `RateLimiterProperties` with configurable limits per key type
   - Environment variable-based configuration
   - Supports different limits for user, service, IP, and auth endpoints
   - Configuration validation and type-safe property access

4. **Metrics and Observability**
   - `ThrottleMetricsHandler` integrated with metrics collection interceptor
   - Four metric types recorded:
     - `rate.limit.requests` - allowed vs blocked requests by key type
     - `rate.limit.violations` - specific identifiers (user IDs, service IDs, IPs) that exceeded limits
     - `rate.limit.utilization` - percentage utilization (0-100%) by key type and service
     - `rate.limit.failures` - rate limiting system failures (e.g., Redis connection issues)
   - Grafana dashboard "Bravo Throttle Metrics" with panels for:
     - Rate limit requests (allowed vs blocked)
     - Violations by key type and identifier
     - Utilization by key type (average and max)
     - Utilization by service (average and max)
     - Violations by service
     - System failures

5. **Request Context Integration**
   - Rate limit data stored in request context properties for metrics handler access
   - `RateLimitKeyParser` utility for extracting key type and identifier from rate limit keys
   - Constants centralized in `RateLimitKeyParser` for cross-library reuse

6. **Response Headers**
   - 429 responses include `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` headers
   - Clear error messages for rate limit violations

**Remaining Work:**

1. **Distributed Rate Limiting (Future Enhancement)**
   - Current implementation uses in-memory Bucket4j buckets (per-instance limits)
   - For multi-instance deployments, consider Redis-backed distributed rate limiting
   - Use `quarkus-redis-client` with Bucket4j Redis integration
   - Effort: Medium (2-3 days)

2. **AWS WAF Integration (Production)**
   - Layer 7 protection at ALB/CloudFront
   - Geographic restrictions if needed
   - Additional DDoS protection beyond application-level limits
   - Effort: Low-Medium (1-2 days)

3. **Service-to-Service Rate Limiting (Optional)**
   - Explicit rate limiting for service-to-service calls
   - Currently handled by per-service limits, but could be more granular
   - Effort: Low (0.5 days)

**See:** `docs/architecture/METRICS_USAGE.md` for rate limiting metrics documentation

**Effort Remaining:** ~3-5 days (for distributed rate limiting and WAF integration)  
**Value:** High - security and cost protection, production-ready for single-instance deployments

---

## Priority 2: Observability & Monitoring

### 2.1 Metrics Dashboard & Alerting

**Current State:** Five dashboards operational: HTTP metrics, User metrics, JVM metrics, Rate Limiting metrics, and Infrastructure metrics (see 1.1). Production alerting and CloudWatch integration deferred to production infrastructure work.

**Completed:**
- ✅ Prometheus + Grafana infrastructure in `compose.yml`
- ✅ Five pre-configured dashboards:
  - Request rates and latencies (HTTP metrics)
  - Authentication attempts (User metrics)
  - JVM metrics (heap, GC, threads)
  - Rate limiting metrics (requests, violations, utilization)
  - Infrastructure metrics (external API calls, database operations, connection pools)

**Deferred Work:**

1. **Production Alerting** (deferred to production infrastructure work)
   - CloudWatch Dashboards (migrate from Grafana or replicate)
   - CloudWatch Alarms for:
     - High error rates
     - High latency (p95, p99)
     - Dependency health check failures
     - Rate limit violations (high volume)

**Effort:** Medium (2-3 days) - deferred to production infrastructure work  
**Value:** High - operational visibility

---

### 2.2 Structured Logging Enhancement

**Current State:** JSON logging configured but may need enhancement.

**Recommendations:**

1. **Enforce Structured Logging**
   - Use MDC (Mapped Diagnostic Context) for:
     - Request ID (correlation ID)
     - User ID
     - Service ID
     - Trace ID (from OpenTelemetry)

2. **Log Levels Strategy**
   - ERROR: System errors, exceptions
   - WARN: Degraded performance, circuit breaker open
   - INFO: Business events (user login, document upload)
   - DEBUG: Detailed request/response (dev only)

3. **Sensitive Data Masking**
   - Automatically mask PII in logs
   - Mask tokens, passwords, API keys

**Effort:** Low (1 day)  
**Value:** Medium - compliance and debugging

---

### 2.3 Distributed Tracing Enhancement

**Current State:** OpenTelemetry enabled, traces sent to Jaeger.

**Recommendations:**

1. **Add Custom Spans**
   - Database query spans
   - External API call spans (TextKernel, Cognito)
   - Business operation spans (document parsing, matching)

2. **Trace Sampling Strategy**
   - 100% sampling in development
   - Adaptive sampling in production (sample rate based on error rate)

3. **Baggage Propagation**
   - Use OpenTelemetry baggage for business context
   - Propagate user preferences, feature flags

**Effort:** Low-Medium (1-2 days)  
**Value:** Medium - debugging complex flows

---

## Priority 3: Code Quality & Maintainability

### 3.1 Cyclomatic Complexity Metrics

**Current State:** Static analysis present but no complexity metrics.

**Impact:** Cannot identify overly complex code that's hard to maintain.

**Recommendations:**

1. **Add Complexity Analysis**
   - Use `javancss-maven-plugin` or `sonar-maven-plugin`
   - Set complexity thresholds:
     - Warning at 10
     - Error at 15
   - Fail build on high complexity

2. **CI Integration**
   - Add complexity report to static analysis workflow
   - Track complexity trends over time

**Effort:** Low (0.5 days)  
**Value:** Medium - long-term maintainability

---

### 3.2 Test Coverage Metrics

**Current State:** Tests exist but coverage not measured.

**Recommendations:**

1. **Add JaCoCo Maven Plugin**
   ```xml
   <plugin>
       <groupId>org.jacoco</groupId>
       <artifactId>jacoco-maven-plugin</artifactId>
       <executions>
           <execution>
               <goals>
                   <goal>prepare-agent</goal>
               </goals>
           </execution>
           <execution>
               <id>report</id>
               <goals>
                   <goal>report</goal>
               </goals>
           </execution>
       </executions>
   </plugin>
   ```

2. **Set Coverage Thresholds**
   - Minimum 80% line coverage
   - Minimum 70% branch coverage
   - Fail build if below threshold

3. **Coverage Reports in CI**
   - Upload coverage reports to GitHub Actions artifacts
   - Consider Codecov or similar for trend tracking

**Effort:** Low (0.5 days)  
**Value:** Medium - quality assurance

---

### 3.3 API Documentation Enhancement

**Current State:** OpenAPI/Swagger UI enabled.

**Recommendations:**

1. **Enhance OpenAPI Annotations**
   - Add detailed descriptions for all endpoints
   - Document error responses (400, 401, 403, 500)
   - Add examples for request/response bodies
   - Document authentication requirements

2. **API Versioning Strategy**
   - Implement URL-based versioning (`/api/v1/...`)
   - Document deprecation policy
   - Add version to OpenAPI spec

**Effort:** Low-Medium (1-2 days)  
**Value:** Medium - developer experience

---

## Priority 4: Performance & Scalability

### 4.1 Performance Testing Framework

**Current State:** Not implemented (user mentioned interest).

**Impact:** Cannot validate fault tolerance under load or identify bottlenecks.

**Recommendations:**

1. **Load Testing Tool Selection**
   - **Option A:** k6 (recommended - modern, scriptable, CI-friendly)
   - **Option B:** Gatling (JVM-based, good for complex scenarios)
   - **Option C:** Apache JMeter (mature, GUI available)

2. **Performance Test Scenarios**
   - Baseline: Single user, typical flow
   - Load: Expected production load
   - Stress: Beyond expected load
   - Spike: Sudden traffic increase
   - Endurance: Sustained load over time

3. **Test Circuit Breakers**
   - Simulate downstream service failures
   - Verify circuit breaker opens/closes correctly
   - Test retry behavior

4. **CI Integration**
   - Run performance tests on schedule (nightly/weekly)
   - Fail if performance degrades significantly
   - Store results for trend analysis

**Effort:** Medium-High (4-5 days)  
**Value:** High - validates architecture under load

**Note:** Can be done on laptop initially, but will need infrastructure for realistic testing.

---

### 4.2 Database Connection Pooling & Optimization

**Current State:** Using Quarkus Hibernate (default connection pool).

**Recommendations:**

1. **Configure Connection Pool**
   ```properties
   quarkus.datasource.jdbc.max-size=20
   quarkus.datasource.jdbc.min-size=5
   quarkus.datasource.jdbc.idle-removal-interval=5M
   quarkus.datasource.jdbc.max-lifetime=30M
   ```

2. **Add Connection Pool Metrics**
   - Active connections
   - Idle connections
   - Wait time for connections
   - Connection acquisition failures

3. **Query Performance Monitoring**
   - Log slow queries (>100ms)
   - Track query execution time in metrics
   - Use Hibernate statistics (dev only)

**Effort:** Low (0.5 days)  
**Value:** Medium - performance optimization

---

### 4.3 Caching Strategy

**Current State:** No caching layer identified.

**Recommendations:**

1. **Application-Level Caching**
   - Cache Cognito token validation results (short TTL)
   - Cache user profile data
   - Cache parsed document results (if idempotent)

2. **Distributed Caching (Future)**
   - Redis/ElastiCache for multi-instance deployments
   - Cache invalidation strategy

**Effort:** Medium (2-3 days)  
**Value:** Medium - performance and cost reduction

---

## Priority 5: 12-Factor App Compliance

### 5.1 Configuration Management Audit

**Current State:** Using SmallRye Config, environment variables, AWS SSM.

**12-Factor Compliance Check:**

✅ **I. Codebase** - Single codebase, multiple deploys  
✅ **II. Dependencies** - Maven manages dependencies  
✅ **III. Config** - Config in environment variables  
✅ **IV. Backing Services** - Treated as attached resources  
✅ **V. Build, release, run** - Separate stages  
✅ **VI. Processes** - Stateless processes  
✅ **VII. Port binding** - Quarkus binds to port  
✅ **VIII. Concurrency** - Process model (horizontal scaling)  
✅ **IX. Disposability** - Fast startup/shutdown  
✅ **X. Dev/prod parity** - Docker Compose for local  
✅ **XI. Logs** - Logs as event streams  
✅ **XII. Admin processes** - Taskfile for admin tasks  

**Recommendations:**

1. **Configuration Validation**
   - Fail fast on missing required config
   - Validate config values at startup
   - Document all configuration properties

2. **Environment-Specific Config**
   - Use profiles for dev/staging/prod
   - Keep secrets out of code (already done)

**Effort:** Low (1 day)  
**Value:** Low - mostly compliant already

---

### 5.2 Graceful Shutdown

**Current State:** Not explicitly configured.

**Recommendations:**

1. **Configure Graceful Shutdown**
   ```properties
   quarkus.shutdown.timeout=30
   quarkus.shutdown.wait-for-shutdown-hook=true
   ```

2. **Health Check Integration**
   - Return 503 during shutdown
   - ALB will stop sending traffic

3. **Resource Cleanup**
   - Close database connections
   - Flush metrics
   - Complete in-flight requests

**Effort:** Low (0.5 days)  
**Value:** Medium - zero-downtime deployments

---

## Priority 6: Operational Excellence

### 6.1 Database Migration Strategy

**Current State:** Not clear if migrations are automated.

**Recommendations:**

1. **Use Flyway or Liquibase**
   - Flyway recommended (simpler, Quarkus native support)
   - Version-controlled migrations
   - Automatic migration on startup (dev) or manual (prod)

2. **Migration Best Practices**
   - Backward-compatible migrations
   - Rollback scripts
   - Test migrations in staging first

**Effort:** Medium (2-3 days)  
**Value:** High - database schema management

---

### 6.2 Feature Flags

**Current State:** Not implemented.

**Recommendations:**

1. **Simple Feature Flags (Initial)**
   - Environment variable-based flags
   - Per-service feature toggles

2. **Advanced Feature Flags (Future)**
   - AWS AppConfig or LaunchDarkly
   - Per-user, per-region flags
   - A/B testing support

**Effort:** Low-Medium (1-2 days for simple)  
**Value:** Medium - safe deployments

---

### 6.3 API Request/Response Validation

**Current State:** Not explicitly configured.

**Recommendations:**

1. **Bean Validation**
   - Use Jakarta Bean Validation (`@NotNull`, `@Size`, `@Email`, etc.)
   - Validate DTOs at REST endpoints
   - Return 400 with validation errors

2. **Custom Validators**
   - Business rule validation
   - Cross-field validation

**Effort:** Low (1 day)  
**Value:** Medium - data quality

---

## Implementation Roadmap

### Phase 1: Critical Production Blockers (Week 1-2)
1. ✅ Metrics implementation (1.1) - ~95% complete (all application-level metrics implemented; CloudWatch integration deferred to production infrastructure work)
2. ✅ Enhanced health checks (1.2) - ~90% complete
3. ✅ Rate limiting (1.3) - ~95% complete (distributed rate limiting deferred)

### Phase 2: Observability (Week 3-4)
1. Metrics dashboard (2.1)
2. Structured logging enhancement (2.2)
3. Distributed tracing enhancement (2.3)

### Phase 3: Code Quality (Week 5)
1. Complexity metrics (3.1)
2. Test coverage (3.2)
3. API documentation (3.3)

### Phase 4: Performance (Week 6-7)
1. Performance testing framework (4.1)
2. Database optimization (4.2)
3. Caching strategy (4.3)

### Phase 5: Operational Excellence (Week 8)
1. Database migrations (6.1)
2. Graceful shutdown (5.2)
3. Request validation (6.3)

---

## References

- [12-Factor App](https://12factor.net/)
- [Quarkus Metrics Guide](https://quarkus.io/guides/micrometer)
- [Quarkus Health Guide](https://quarkus.io/guides/smallrye-health)
- [MicroProfile Fault Tolerance](https://microprofile.io/project/eclipse/microprofile-fault-tolerance)
- [OpenTelemetry Best Practices](https://opentelemetry.io/docs/specs/otel/best-practices/)

---

## Notes

- **Performance Testing:** Can start on laptop but will need infrastructure for realistic load testing
- **Infrastructure Provisioning:** Intentionally deferred per user preference
- **Feature Development:** Excluded from this analysis (per user request)

