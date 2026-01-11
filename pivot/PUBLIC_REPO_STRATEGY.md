# Public Repository Strategy: What to Expose

This document outlines what can be safely exposed in your public repository to demonstrate value without giving away the core implementation.

> **📦 Dependency Strategy**: See [PUBLIC_REPO_DEPENDENCY_STRATEGY.md](PUBLIC_REPO_DEPENDENCY_STRATEGY.md) for details on whether to use real Maven dependencies or reference-only copies.

## Strategy Overview

**Goal**: Showcase technical architecture and patterns while protecting:
- Complete implementations
- Domain-specific business logic
- Configuration secrets
- Full service implementations

**Approach**: Expose **interfaces, contracts, examples, and architectural patterns** that demonstrate sophistication without revealing implementation details.

---

## ✅ Safe to Expose (High Value, Low Risk)

### 1. **Annotation Interfaces** (Already Identified)

These are pure contracts with no implementation:

- **`@Secured`** - Authentication annotation
- **`@AllowedServices`** - Service-level authorization annotation
- **`@ServiceMetrics`** - Metrics collection annotation

**Why safe**: Annotations are just metadata. The value is in the implementation, which stays private.

**Location**: `libs/security/src/main/java/tech/eagledrive/security/presentation/rest/`

### 2. **Domain Interfaces** (Architectural Contracts)

These define the architecture without revealing implementation:

- **`TokenValidator`** - JWT token validation contract
- **`MetricsRecorder`** - Metrics recording contract
- **`RateLimiter`** - Rate limiting contract
- **`ServiceAuthenticationProvider`** - Service auth contract
- **`ServiceTokenProvider`** - Service token management contract
- **`MetricsResultIndicator`** - Metrics result marker interface

**Why safe**: Interfaces define contracts, not implementations. Shows architectural sophistication.

**Location**: `libs/*/src/main/java/tech/eagledrive/*/domain/`

### 3. **Example/Demo Resources** (Sanitized)

Minimal examples that demonstrate usage patterns:

- **`TestResource`** - Shows `@Secured` usage (already test-only)
- Create sanitized demo resources showing:
  - How to use `@Secured`
  - How to use `@AllowedServices`
  - How to use `@ServiceMetrics`
  - Service-to-service communication patterns

**Why safe**: Examples show patterns, not business logic. Can be generic.

**Example structure**:
```java
// Public example - generic domain
@Path("/api/examples")
public class ExampleResource {
    
    @GET
    @Secured
    public Response getExample() {
        return Response.ok("Example data").build();
    }
    
    @POST
    @Secured
    @AllowedServices({"example-service"})
    public Response processExample(ExampleRequest request) {
        return Response.ok().build();
    }
}
```

### 4. **Architecture Decision Records (ADRs)** (Sanitized)

Your ADRs are gold for demonstrating thought process, but remove domain-specific details:

**Safe to expose**:
- ADR-0012: Clean Architecture Package Structure
- ADR-0011: Stateless JWT Authentication (remove Cognito-specific details)
- ADR-0007: Observability Strategy
- ADR-0014: Application Caching Strategy
- ADR-0010: REST API Design Standards

**Sanitize**:
- Remove references to "recruitment", "candidate", "job", "resume"
- Replace with generic terms: "entity", "resource", "document"
- Remove service-specific names (keep generic patterns)

**Location**: `docs/architecture/decisions/`

### 5. **Clean Architecture Package Structure Standards**

The package structure ADR is perfect for public exposure - it shows architectural maturity.

**Location**: `docs/architecture/decisions/0012-clean-architecture-package-structure.md`

### 6. **Library READMEs** (Sanitized)

Your library READMEs are excellent marketing material:

- **`libs/security/README.md`** - Shows security architecture
- Create sanitized versions that:
  - Remove domain-specific examples
  - Use generic examples
  - Keep all technical details

**Example sanitization**:
```markdown
# Before (domain-specific)
@Path("/api/resume")
public class ResumeResource {
    @POST
    @Secured
    public Response uploadResume(ResumeRequest request) {
        // ...
    }
}

# After (generic)
@Path("/api/documents")
public class DocumentResource {
    @POST
    @Secured
    public Response uploadDocument(DocumentRequest request) {
        // ...
    }
}
```

### 7. **Architecture Documentation** (Sanitized)

High-level architecture docs that show sophistication:

**Safe to expose** (with sanitization):
- **`AUTHENTICATION.md`** - Remove Cognito-specific details, keep patterns
- **`SERVICE_TO_SERVICE_AUTH.md`** - Excellent for showing security architecture
- **`HEALTH_CHECKS_IMPLEMENTATION.md`** - Shows operational maturity
- **`CACHING_STRATEGY.md`** - Shows performance thinking
- **`METRICS_IMPLEMENTATION_STATUS.md`** - Shows observability maturity

**Sanitize by**:
- Replacing domain terms with generics
- Removing service-specific names
- Keeping all technical patterns and decisions

### 8. **CI/CD Workflow Examples** (Generic Patterns)

Show operational maturity with generic workflow examples:

**Create sanitized versions**:
- Build/test workflow (remove domain-specific steps)
- Static analysis workflow
- Deployment patterns (generic, no secrets)

**What to show**:
- Workflow structure
- Quality gates
- Testing strategies
- Deployment patterns (without actual configs)

### 9. **Configuration Examples** (Sanitized Templates)

Show configuration patterns without real values:

- Quarkus `application.properties` templates
- Docker Compose examples (generic services)
- Environment variable templates

**Template example**:
```properties
# application.properties.template
# Authentication
quarkus.security.jwt.issuer=${JWT_ISSUER}
quarkus.security.jwt.audience=${JWT_AUDIENCE}

# Database
quarkus.datasource.jdbc.url=${DATABASE_URL}
quarkus.datasource.username=${DATABASE_USERNAME}
quarkus.datasource.password=${DATABASE_PASSWORD}
```

### 10. **Test Utilities** (Generic)

Generic test utilities that show testing patterns:

- Test resource examples (like `TestResource`)
- Test configuration patterns
- Integration test setup examples

---

## ⚠️ Potentially Safe (With Careful Sanitization)

### 1. **Domain DTOs** (Interfaces Only)

If you have DTO interfaces that are pure contracts, expose those. But be careful:
- Only expose interfaces/markers
- Remove any domain-specific DTOs
- Keep only generic patterns

### 2. **Client Interfaces** (Service Contracts)

Service client interfaces can show API design:
- `AuthServiceClient` interface (not implementation)
- Generic REST client patterns

**Location**: `libs/domain-clients/`

### 3. **Common Utilities** (Generic Only)

Generic utilities that aren't domain-specific:
- JSON utilities
- Validation utilities
- Logging utilities
- Base64 utilities

**Location**: `libs/common/src/main/java/tech/eagledrive/common/`

**Be careful**: Review each utility to ensure it's truly generic.

---

## ❌ Do NOT Expose

### 1. **Full Service Implementations**
- All code in `services/` directory
- All code in `applications/` directory
- Repository implementations
- Service implementations
- Business logic

### 2. **Infrastructure Implementations**
- Actual interceptor implementations
- Filter implementations
- Token validator implementations
- Metrics recorder implementations
- Rate limiter implementations

### 3. **Domain-Specific Code**
- Any code referencing "candidate", "resume", "job", "match"
- Business domain models
- Domain services
- Domain repositories

### 4. **Configuration Files**
- Real `application.properties` files
- Real environment configurations
- Secrets or credentials (even templates with real structure)
- AWS resource names
- Database schemas

### 5. **Complete Library Implementations**
- Keep library implementations private
- Only expose interfaces and annotations

### 6. **Deployment Scripts**
- Actual deployment scripts with real resource names
- AWS setup scripts with real configurations
- Database migration scripts

---

## Recommended Public Repository Structure

```
public-repo/
├── README.md                          # Marketing-focused, technical overview
├── docs/
│   ├── architecture/
│   │   ├── decisions/
│   │   │   ├── 0012-clean-architecture-package-structure.md
│   │   │   ├── 0011-stateless-jwt-authentication.md (sanitized)
│   │   │   └── ... (other sanitized ADRs)
│   │   ├── AUTHENTICATION.md (sanitized)
│   │   ├── SERVICE_TO_SERVICE_AUTH.md (sanitized)
│   │   └── ... (other sanitized docs)
│   └── examples/
│       ├── security/
│       │   ├── SecuredExample.java
│       │   └── AllowedServicesExample.java
│       ├── metrics/
│       │   └── ServiceMetricsExample.java
│       └── architecture/
│           └── clean-architecture-example/
├── examples/
│   └── minimal-service/
│       ├── pom.xml
│       └── src/main/java/
│           └── ExampleResource.java
├── contracts/
│   ├── security/
│   │   ├── Secured.java
│   │   ├── AllowedServices.java
│   │   ├── TokenValidator.java
│   │   └── RateLimiter.java
│   └── metrics/
│       ├── ServiceMetrics.java
│       ├── MetricsRecorder.java
│       └── MetricsResultIndicator.java
├── templates/
│   ├── application.properties
│   └── docker-compose.example.yml
└── .github/
    └── workflows/
        └── examples/
            ├── build-test-example.yml
            └── static-analysis-example.yml
```

---

## Value Proposition Summary

What you're demonstrating:

1. **Architectural Maturity**
   - Clean Architecture implementation
   - Well-documented ADRs
   - Consistent package structure

2. **Security Sophistication**
   - Zero-trust architecture
   - Service-to-service authentication
   - Fine-grained authorization patterns

3. **Operational Excellence**
   - Comprehensive observability
   - Health check patterns
   - Metrics and monitoring

4. **Developer Experience**
   - Simple annotations for complex features
   - Clear contracts and interfaces
   - Well-documented patterns

5. **Production Readiness**
   - Fault tolerance patterns
   - Caching strategies
   - CI/CD workflows

---

## Next Steps

1. **Create sanitized versions** of ADRs and architecture docs
2. **Extract interfaces** from libraries into `contracts/` directory
3. **Create example resources** showing usage patterns
4. **Write marketing-focused README** highlighting technical achievements
5. **Set up public repo structure** as outlined above
6. **Add license** (consider a commercial license or dual licensing)

---

## Licensing Considerations

Since you want to monetize:

- **Private repo**: Keep proprietary, no license needed
- **Public repo**: Consider:
  - **Commercial license** (no free use)
  - **Dual licensing** (AGPL for open source, commercial for proprietary)
  - **Source-available** (view but not use without license)
  - **Fair use license** (view for evaluation, commercial use requires license)

Consult with a lawyer for the best licensing strategy for your monetization goals.
