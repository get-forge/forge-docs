# Public Repository Code Strategy

> **📋 Related Document**: See [PUBLIC_REPO_STRATEGY.md](./PUBLIC_REPO_STRATEGY.md) for the complete strategy and current migration status.

## Current State

**Migration Status**: 
- ✅ **MIGRATED**: All code components (Health Checks, Metrics Framework, Generic Utilities, Rate Limiting Core, Method Entry Logging, Validation Exception Mapper, Test Utilities, Taskfiles, Circuit Breaker Exception Mapper, Static Analysis Configurations, Code Formatting Configurations, Git Hooks Config, Maven Build Configuration Patterns, Docker Compose, Scripts)
- ⏳ **REMAINING**: CI/CD badges, ADRs, READMEs, Code examples

---

## Remaining Work

### 1. CI/CD Badges ⭐⭐⭐⭐⭐ ⏳ **NEXT**

**What**: Set up CI/CD badges in public repo README
- Workflow status badges
- Code coverage badges (Codecov)
- Code quality metrics

**Action**: 
- Configure GitHub Actions workflows to publish badges
- Set up Codecov integration
- Add badges to README.md

**Time**: 15-30 minutes

---

### 2. Architecture Decision Records (ADRs) ⭐⭐⭐

**What**: Sanitize and migrate ADRs from `docs/architecture/decisions/`

**Safe to expose** (with sanitization):
- ADR-0012: Clean Architecture Package Structure (already generic)
- ADR-0011: Stateless JWT Authentication (remove Cognito-specific details)
- ADR-0007: Observability Strategy
- ADR-0014: Application Caching Strategy
- ADR-0010: REST API Design Standards

**Sanitization checklist**:
- [ ] Remove references to "recruitment", "candidate", "job", "resume"
- [ ] Replace with generic terms: "entity", "resource", "document"
- [ ] Remove service-specific names (keep generic patterns)
- [ ] Remove Cognito-specific implementation details (keep patterns)

**Location**: `docs/architecture/decisions/` → `forge-kit/docs/architecture/decisions/`

---

### 3. Library READMEs ⭐⭐⭐

**What**: Sanitize existing READMEs and create usage examples

**Files to sanitize**:
- `libs/security/README.md` (if exists)
- `libs/metrics/README.md` (if exists)
- `libs/health/README.md` (if exists)
- Other library READMEs

**Sanitization checklist**:
- [ ] Replace domain-specific examples with generic ones
- [ ] Remove service-specific references
- [ ] Keep all technical details and patterns
- [ ] Add usage examples showing how to implement

**Example transformation**:
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

---

### 4. Code Examples ⭐⭐⭐

**What**: Create examples showing how to implement patterns

**Examples to create**:

#### Security Examples
- `examples/security/SecuredExample.java` - Shows `@Secured` usage
- `examples/security/AllowedServicesExample.java` - Shows `@AllowedServices` usage
- `examples/security/ServiceToServiceExample.java` - Shows service-to-service patterns

#### Metrics Examples
- `examples/metrics/ServiceMetricsExample.java` - Shows `@ServiceMetrics` usage

#### Architecture Examples
- `examples/architecture/clean-architecture-example/` - Minimal service showing Clean Architecture structure

**Example template**:
```java
/**
 * Example Resource demonstrating security annotations usage.
 * 
 * This is a sanitized, generic example that shows architectural patterns
 * without revealing domain-specific business logic.
 */
package io.forge.kit.examples.presentation.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.forge.kit.security.contracts.AllowedServices;
import io.forge.kit.security.contracts.Secured;

/**
 * Example REST resource demonstrating:
 * - @Secured annotation for user authentication
 * - @AllowedServices annotation for service-level authorization
 * - Clean Architecture presentation layer patterns
 */
@Path("/api/examples")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExampleResource {
    
    /**
     * Secured endpoint - requires user authentication.
     * Demonstrates @Secured annotation usage.
     */
    @GET
    @Path("/secured")
    @Secured
    public Response getSecuredData() {
        // User is automatically authenticated via @Secured annotation
        return Response.ok("Secured data").build();
    }
    
    /**
     * Service-only endpoint - requires service authentication.
     * Demonstrates @AllowedServices annotation for service-level authorization.
     */
    @POST
    @Path("/process")
    @Secured
    @AllowedServices({"processing-service"})
    public Response processData(ExampleRequest request) {
        // Only processing-service can call this endpoint
        return Response.ok("Processed").build();
    }
}
```

---

## Quick Reference: Domain Term Replacements

| Domain Term | Generic Replacement |
|------------|---------------------|
| candidate | user, entity, participant |
| resume | document, file, resource |
| job | position, item, resource |
| match | process, operation, correlation |
| client | customer, consumer |
| investor | stakeholder, partner |
| candidate-service | entity-service, user-service |
| document-service | storage-service, file-service |
| match-service | processing-service, correlation-service |

---

## Quick Reference: What NOT to Include

- ❌ Full service implementations
- ❌ Repository implementations
- ❌ Business logic
- ❌ Real configuration values
- ❌ Secrets or credentials
- ❌ AWS resource names
- ❌ Database schemas
- ❌ Domain-specific DTOs
- ❌ Complete library implementations
- ❌ Deployment scripts with real configs
- ❌ application.properties files (security reasons)

---

## Quick Reference: What TO Include

- ✅ CI/CD badges and quality metrics
- ✅ Architecture Decision Records (sanitized)
- ✅ Library READMEs (sanitized)
- ✅ Code examples (generic, showing patterns)
- ✅ Configuration templates (sanitized)
- ✅ CI/CD workflow examples (generic)
- ✅ Package structure standards
- ✅ Usage patterns and examples

---

## Next Steps

1. ⏳ **Set up CI/CD badges** (Tier 13) - 15-30 minutes, highest ROI
2. ⏳ **Sanitize ADRs** - Review and sanitize Architecture Decision Records
3. ⏳ **Sanitize READMEs** - Review and sanitize library READMEs
4. ⏳ **Create code examples** - Create examples showing how to implement patterns

Once complete, these documents can be deleted as all work will be finished.
