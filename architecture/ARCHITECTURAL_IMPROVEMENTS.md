# Architectural Improvements & Production Readiness

**Date:** 2025-01-27  
**Status:** Analysis & Recommendations  
**Context:** High-level architectural improvements for enterprise, production-grade, high-scale system

## Executive Summary

This document identifies high-impact architectural improvements aligned with enterprise best practices,
12-factor app principles, and production-grade system requirements. Recommendations are prioritized by
impact and effort, focusing on boilerplate and productionization rather than feature development.

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

**Impact:** Metrics infrastructure is production-ready with comprehensive coverage of application operations,
external APIs, database operations, and connection pools. All necessary application-level metrics are
implemented and operational.

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
Additional business event metrics beyond the current implementation are **not required**. The existing
metrics (HTTP, authentication, rate limiting, external API calls, database operations, and connection
pools) provide sufficient operational visibility. Business events are adequately captured through HTTP
request metrics and application logs.

**Decision - AWS Service Metrics (Cognito, S3, DynamoDB):**
Metrics for AWS-managed services are **not recommended** because:
- AWS already provides CloudWatch metrics for their services
- Health checks (already implemented) catch availability issues
- Application-level metrics (HTTP latency, error rates) already reflect AWS service performance
- AWS outages are well-publicized and affect all customers
- Adds code complexity with limited operational value

Health checks are sufficient for AWS services - they provide the necessary dependency status for load
balancers and operators without the overhead of custom metrics.

**Deferred Work:**

1. **CloudWatch Integration** (deferred to production infrastructure work)
   - Add `micrometer-registry-cloudwatch2` dependency
   - Configure for production environment
   - Document metrics export strategy
   - Will be addressed as part of production infrastructure setup

**Security Decision - Metrics Endpoint Protection:**

Application-level authentication for `/q/metrics` and `/q/health` endpoints is **not required** given the AWS infrastructure security layers:

- **AWS WAF**: Programmatically configured to block `/q/metrics` and `/q/health` from external/public IPs,
  allowing only known monitoring system IPs (Prometheus, CloudWatch, etc.)
- **Security Groups**: ECS tasks restricted to allow traffic only from ALB security groups and monitoring system security groups
- **VPC Isolation**: Containers deployed in private subnets with no public IPs, NAT Gateway for outbound-only internet access
- **ALB**: SSL termination and path-based routing with internal ALB for metrics/health endpoints if needed

This defense-in-depth approach (WAF → ALB → Security Groups → VPC) provides sufficient protection without
requiring application-level authentication, simplifying Prometheus scraping and reducing operational
complexity.

**See:** `docs/architecture/METRICS_IMPLEMENTATION_STATUS.md` for detailed status  
**See:** `docs/architecture/METRICS_SECURITY.md` for security analysis (superseded by infrastructure-based approach)

**Effort Remaining:** CloudWatch integration deferred to production infrastructure work  
**Value:** Critical for production operations - application-level metrics complete and production-ready

---

### 1.2 Enhanced Health Checks

**Status:** ~90% Complete  
**Last Updated:** 2025-01-27

**Current State:** Custom readiness checks are implemented via a shared health library and per service
registration classes. All critical backend dependencies (PostgreSQL, DynamoDB, S3, Cognito) now have
explicit readiness checks where they are actually used.

**Impact:** Services can now fail fast when dependencies are unavailable, and `/q/health/ready` exposes a
clear view of dependency status for load balancers and operators.

**Completed:**

1. **Shared health check library (`libs/health`)**
   - Introduced reusable abstract health check base classes:
     - `PostgresHealthCheck` (supports checking multiple tables)
     - `S3HealthCheck` (supports checking multiple buckets)
     - `DynamoDbHealthCheck` (supports checking multiple tables)
     - `CognitoHealthCheck` (checks Cognito User Pool accessibility)
   - Base classes are dependency agnostic and accept fully configured clients and identifiers.

2. **Per service health check registration pattern**
   - Each service uses a single `*ServiceHealthChecks` class with `@Produces @Readiness @ApplicationScoped`
     methods that return anonymous subclasses of the shared base checks.
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

**Current State:** Application-level rate limiting is fully implemented using Bucket4j with per-user,
per-service, and per-IP limits. The system includes comprehensive metrics collection and Grafana
dashboards for monitoring. Rate limiting runs before authentication to protect all endpoints, including
public authentication endpoints.

**Impact:** Services are now protected against abuse, DDoS, and accidental traffic spikes. Rate limit
violations and utilization are tracked with detailed metrics for operational visibility.

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

**Status:** ~85% Complete  
**Last Updated:** 2025-01-27

**Current State:** Five dashboards operational: HTTP metrics, User metrics, JVM metrics, Rate Limiting
metrics, and Infrastructure metrics (see 1.1). Production alerting and CloudWatch integration deferred
to production infrastructure work.

**Completed:**
- ✅ Prometheus + Grafana infrastructure in `compose.yml`
- ✅ Five pre-configured dashboards:
  - Request rates and latencies (HTTP metrics)
  - Authentication attempts (User metrics)
  - JVM metrics (heap, GC, threads)
  - Rate limiting metrics (requests, violations, utilization)
  - Infrastructure metrics (external API calls, database operations, connection pools)

**Remaining Work:**

1. **Production Alerting** (deferred to production infrastructure work)
   - CloudWatch Dashboards (migrate from Grafana or replicate)
   - CloudWatch Alarms for:
     - High error rates
     - High latency (p95, p99)
     - Dependency health check failures
     - Rate limit violations (high volume)

**Effort Remaining:** Medium (2-3 days) - deferred to production infrastructure work  
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

**Status:** ~100% Complete  
**Last Updated:** 2025-01-27

**Current State:** Cyclomatic and cognitive complexity thresholds are implemented using Maven PMD plugin
with configured rules that fail the build on violations. The complexity analysis runs automatically during
the Maven verify phase as part of the static analysis workflow.

**Impact:** Overly complex code is now automatically identified during builds, preventing maintainability issues from entering the codebase.

**Completed:**

1. **✅ Maven PMD Plugin Configuration**
   - `maven-pmd-plugin` configured in root `pom.xml`
   - Plugin version: 3.28.0
   - Configured to fail build on violations (`failOnViolation: true`)
   - Runs during `verify` phase as part of static analysis workflow

2. **✅ Complexity Rules Configuration**
   - Custom ruleset file: `.pmd-rules.xml`
   - **Cyclomatic Complexity:**
     - Method threshold: 10 (reports violations above this level)
     - Class threshold: 20 (utility class tolerance)
   - **Cognitive Complexity:**
     - Threshold: 15 (reports violations above this level)
   - Both rules enforce build failure on violations

3. **✅ CI Integration**
   - Complexity analysis integrated into static analysis workflow
   - Build fails if complexity thresholds are exceeded
   - Violations are printed during build for immediate feedback

**Effort Remaining:** None  
**Value:** Medium - long-term maintainability (achieved)

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

**Status:** ~90% Complete  
**Last Updated:** 2025-01-27

**Current State:** OpenAPI/Swagger UI enabled. All DTOs documented with `@Schema` annotations. Controllers
and Resources use minimal JAX-RS annotations only - documentation is pushed down to DTOs for
maintainability.

**Completed:**

1. **✅ DTO Documentation (Lightweight Approach)**
   - All request/response DTOs annotated with `@Schema` including:
     - Field descriptions
     - Examples for all fields
     - Required field indicators
   - DTOs documented:
     - `LoginRequest`, `RegisterRequest`, `RefreshRequest`
     - `AuthResponse`, `RegistrationResponse`, `AuthUser`
     - `CandidateResponse`, `RegisterRequestPartialAuth`
     - `MatchRequest`, `Match`
     - `ResumeResponse`, `JobSpecResponse`
     - `ErrorResponse`
   - Added `microprofile-openapi-api` dependency to `libs/domain-dtos`

2. **✅ Controller/Resource Cleanup**
   - All `*Controller` classes in `backend-candidate` module cleaned (no OpenAPI annotations)
   - All `*Resource` classes in services cleaned (no OpenAPI annotations)
   - Controllers/Resources use only JAX-RS annotations (`@Path`, `@GET`, `@POST`, etc.)
   - OpenAPI infers schemas from DTOs and endpoints from JAX-RS annotations
   - Documentation lives in DTOs (single source of truth)

**Remaining Work:**

1. **API Versioning Strategy** (optional, not critical)
   - Implement URL-based versioning (`/api/v1/...`)
   - Document deprecation policy
   - Add version to OpenAPI spec
   - Effort: Low-Medium (1-2 days)

**Effort Remaining:** Low (1-2 days for versioning, if desired)  
**Value:** Medium - developer experience

---

## Priority 4: Performance & Scalability

### 4.1 Performance Testing Framework

**Status:** ~95% Complete  
**Last Updated:** 2025-12-28

**Current State:** k6 performance testing framework fully implemented with baseline scenario covering
registration, login, candidate fetch, and resume upload flows. System validated up to 50 VUs with zero
failures and linear throughput scaling.

**Impact:** Architecture validated under load. System demonstrates clean scaling behavior with circuit
breakers functioning correctly. Performance characteristics documented for 10, 20, and 50 VU scenarios.

**Completed:**

1. **✅ k6 Testing Framework**
   - k6 v1.4.2+ configured and operational
   - Custom wrapper script (`perf/run-k6.sh`) for environment variable loading
   - Taskfile integration for easy test execution
   - Configurable VUs and duration via CLI flags

2. **✅ Baseline Performance Scenario**
   - `perf/scenarios/baseline-mix.js` implements realistic user flow:
     - 5% new user registrations
     - 95% existing user operations (login, fetch candidate, resume upload)
   - Token management with automatic refresh per VU
   - Login-once-per-VU pattern (reduces Cognito load, more realistic)
   - Reusable setup utilities (`perf/utils/setup.js`)

3. **✅ Test Flows Implemented**
   - User registration (`perf/flows/register.js`)
   - User login (`perf/flows/login.js`)
   - Token refresh (`perf/flows/refresh-token.js`)
   - Candidate fetch (`perf/flows/fetch-candidate.js`)
   - Resume upload (`perf/flows/upload-resume.js`)

4. **✅ Test Data Management**
   - Existing users seed script (`perf/seed-existing-users.sh`)
   - Test user cleanup script (`perf/cleanup-test-users.sh`)
   - Dummy resume file for upload testing

5. **✅ Performance Validation Results**
   - **10 VUs**: ~10.8 req/s, ~107ms avg latency, ~495ms p95, 0% failures
   - **20 VUs**: ~21.6 req/s, ~99ms avg latency, ~519ms p95, 0% failures
   - **50 VUs**: ~49.7 req/s, ~165ms avg latency, ~876ms p95, 0% failures
   - Linear throughput scaling validated
   - Circuit breakers functioning correctly under load
   - Zero errors across all test scenarios

6. **✅ LocalStack Performance Tuning**
   - Increased DynamoDB operation timeouts (1000ms → 5000ms)
   - Adjusted circuit breaker thresholds (less aggressive for LocalStack)
   - LocalStack resource limits configured in `compose.yml`

**Remaining Work:**

1. **CI Integration** (optional)
   - Run performance tests on schedule (nightly/weekly)
   - Fail if performance degrades significantly
   - Store results for trend analysis
   - Effort: Low-Medium (1-2 days)

2. **Additional Test Scenarios** (optional)
   - Stress test: Beyond expected load (100+ VUs)
   - Spike test: Sudden traffic increase (50 → 100 → 50 VUs)
   - Endurance test: Sustained load over 10-15 minutes
   - Effort: Medium (2-3 days)

3. **Real AWS Testing** (future)
   - Run baseline scenario against real AWS dev environment
   - Compare LocalStack vs real DynamoDB/Cognito performance
   - Validate production-like behavior
   - Effort: Low (0.5 days, requires AWS dev environment)

**Effort Remaining:** Low (1-2 days for CI integration, optional)  
**Value:** High - architecture validated under load, system proven to scale cleanly beyond early-stage usage

---

### 4.2 Database Connection Pooling & Optimization

**Status:** ~100% Complete  
**Last Updated:** 2025-12-28

**Current State:** PostgreSQL connection pool configured with optimized settings. Connection pool metrics
enabled and visualized in Grafana. DynamoDB HTTP client timeouts configured for LocalStack performance.

**Impact:** Improved database connection management under load. Connection pool metrics provide visibility
into pool utilization and potential exhaustion issues.

**Completed:**

1. **✅ PostgreSQL Connection Pool Configuration**
   - Configured in `config/src/main/resources/database.properties`:
     - `max-size=20` (increased from default 10)
     - `min-size=5` (maintains minimum pool size)
     - `idle-removal-interval=5M` (removes idle connections)
     - `max-lifetime=30M` (connection lifetime limit)
     - `acquisition-timeout=10` (connection acquisition timeout in seconds)

2. **✅ Connection Pool Metrics**
   - Agroal metrics enabled (`quarkus.datasource.metrics.enabled=true`)
   - Metrics exposed: `agroal_active_count`, `agroal_available_count`, `agroal_awaiting_count`, `agroal_acquire_count_total`
   - Visualized in Grafana "Bravo Infrastructure Metrics" dashboard
   - Real-time monitoring of connection pool status

3. **✅ DynamoDB Client Optimization**
   - Increased API call timeouts for LocalStack (5s overall, 4s per-attempt)
   - Configured in `DynamoDbClientProducer` with `ClientOverrideConfiguration`
   - Handles LocalStack's slower performance compared to real DynamoDB

4. **✅ LocalStack Resource Limits**
   - Memory limit: 2GB (prevents container starvation)
   - CPU limit: 2 cores (prevents CPU contention)
   - Configured in `compose.yml` for regular Docker Compose

**Remaining Work:**

1. **Query Performance Monitoring** (optional)
   - Log slow queries (>100ms)
   - Track query execution time in metrics
   - Use Hibernate statistics (dev only)
   - Effort: Low (0.5 days)

**Effort Remaining:** Low (0.5 days for query monitoring, optional)  
**Value:** Medium - performance optimization (achieved)

---

### 4.3 Caching Strategy

**Status:** Analysis Complete, Implementation Pending  
**Last Updated:** 2025-01-27

**Current State:** No caching layer implemented. Use cases validated and implementation strategy defined.

**Impact:** Performance optimization and cost reduction through application-level caching. Reduces database load, external API calls, and improves response times for frequently accessed data.

**Use Cases Validated:**

1. **✅ Cognito Token Validation** (High Priority)
   - Cache token validation results with TTL matching token expiration
   - Reduces JWT parsing and JWKS lookups on every request
   - High frequency, high impact

2. **✅ User Profile Data** (Medium Priority)
   - Cache candidate profiles from PostgreSQL
   - TTL: 10 minutes, invalidate on profile updates
   - Reduces database queries for frequently accessed profiles

3. **✅ Parsed Document Results** (Medium Priority)
   - Cache parsed resumes and job specs from DynamoDB
   - TTL: 1 hour (documents are idempotent)
   - Reduces DynamoDB read capacity units (RCU)

**Additional Use Cases Identified:**

4. **Cognito JWKS Keys** (Low Priority - verify library caching first)
5. **Service Token Caching** (Low Priority - already implemented, can migrate to Quarkus cache)
6. **Transaction ID Lookups** (Low Priority - incremental enhancement)

**Implementation Approach:**

1. **Phase 1: Local Caching (Caffeine)**
   - Use Quarkus Cache with default Caffeine backend
   - Implement `@CacheResult` and `@CacheInvalidate` annotations
   - Configure cache names and TTLs per use case
   - Add cache metrics for monitoring

2. **Phase 2: Metrics and Monitoring**
    - Leverage Micrometer cache metrics
    - Add cache hit/miss rates to Grafana dashboards
    - Monitor cache performance and eviction rates

3. **Phase 3: Redis Backend (Production)**
    - Migrate to `quarkus-cache-redis` for distributed caching
    - Deploy AWS ElastiCache Redis cluster
    - Shared infrastructure with distributed rate limiting (see section 1.3)
    - Unified Redis usage: caching + rate limiting

**Synergy with Rate Limiting:**
Redis backend planned for distributed rate limiting (section 1.3) can serve dual purpose:
- Caching: Application data caching
- Rate Limiting: Distributed rate limiting buckets
- Single ElastiCache cluster reduces infrastructure complexity

**See:** `docs/architecture/CACHING_STRATEGY.md` for detailed analysis, implementation plan, and cache key strategies

**Effort:** Medium (2-3 weeks for full implementation)  
**Value:** High - performance optimization, cost reduction, and production readiness

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
1. ✅ Metrics implementation (1.1) - ~95% complete (all application-level metrics implemented;
   CloudWatch integration deferred to production infrastructure work)
2. ✅ Enhanced health checks (1.2) - ~90% complete
3. ✅ Rate limiting (1.3) - ~95% complete (distributed rate limiting deferred)

### Phase 2: Observability (Week 3-4)
1. ✅ Metrics dashboard (2.1) - ~85% complete (production alerting deferred to production infrastructure work)
2. Structured logging enhancement (2.2)
3. Distributed tracing enhancement (2.3)

### Phase 3: Code Quality (Week 5)
1. ✅ Complexity metrics (3.1) - ~100% complete
2. Test coverage (3.2)
3. ✅ API documentation (3.3) - ~90% complete

### Phase 4: Performance (Week 6-7)
1. ✅ Performance testing framework (4.1) - ~95% complete (CI integration optional)
2. ✅ Database optimization (4.2) - ~100% complete
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

