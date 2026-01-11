# Public Repository Creation Checklist

Use this checklist when creating your public repository.

## Phase 1: Preparation

- [ ] Review `PUBLIC_REPO_STRATEGY.md` for full strategy
- [ ] Decide on licensing model (commercial, dual, source-available)
- [ ] Set up new public repository
- [ ] Create repository structure (see strategy doc)

## Phase 2: Extract Interfaces & Annotations

### Security Library
- [ ] Copy `@Secured` annotation to `contracts/security/`
- [ ] Copy `@AllowedServices` annotation to `contracts/security/`
- [ ] Copy `TokenValidator` interface to `contracts/security/`
- [ ] Copy `RateLimiter` interface to `contracts/security/`
- [ ] Copy `ServiceAuthenticationProvider` interface to `contracts/security/`
- [ ] Copy `ServiceTokenProvider` interface to `contracts/security/`
- [ ] Copy `AuthenticationException` to `contracts/security/` (if it's a domain exception)

### Metrics Library
- [ ] Copy `@ServiceMetrics` annotation to `contracts/metrics/`
- [ ] Copy `MetricsRecorder` interface to `contracts/metrics/`
- [ ] Copy `MetricsResultIndicator` interface to `contracts/metrics/`

### Health Library
- [ ] Review health check interfaces - extract if generic enough

### Common Library
- [ ] Review utilities - extract only truly generic ones
- [ ] Extract JSON utilities (if generic)
- [ ] Extract validation utilities (if generic)
- [ ] Extract logging utilities (if generic)

## Phase 3: Sanitize Documentation

### Architecture Decision Records
- [ ] Sanitize ADR-0012 (Clean Architecture) - already generic
- [ ] Sanitize ADR-0011 (JWT Authentication) - remove Cognito specifics
- [ ] Sanitize ADR-0007 (Observability) - remove domain references
- [ ] Sanitize ADR-0014 (Caching) - remove domain references
- [ ] Sanitize ADR-0010 (REST API Design) - remove domain references
- [ ] Review other ADRs - sanitize or exclude domain-specific ones

### Architecture Documentation
- [ ] Sanitize `AUTHENTICATION.md` - remove Cognito/domain specifics
- [ ] Sanitize `SERVICE_TO_SERVICE_AUTH.md` - excellent for public
- [ ] Sanitize `HEALTH_CHECKS_IMPLEMENTATION.md` - remove domain references
- [ ] Sanitize `CACHING_STRATEGY.md` - remove domain references
- [ ] Sanitize `METRICS_IMPLEMENTATION_STATUS.md` - remove domain references
- [ ] Review other architecture docs - sanitize or exclude

### Library READMEs
- [ ] Sanitize `libs/security/README.md` - replace domain examples with generic
- [ ] Create sanitized version of metrics README (if exists)
- [ ] Create sanitized version of health README (if exists)

### Main README
- [ ] Create marketing-focused README
- [ ] Highlight technical achievements
- [ ] Remove all domain-specific references
- [ ] Add "Commercial License" section
- [ ] Add contact/licensing information

## Phase 4: Create Examples

- [ ] Create `examples/security/` directory
- [ ] Create `ExampleResource.java` showing `@Secured` usage
- [ ] Create example showing `@AllowedServices` usage
- [ ] Create example showing service-to-service patterns
- [ ] Create `examples/metrics/` directory
- [ ] Create example showing `@ServiceMetrics` usage
- [ ] Create `examples/architecture/` directory
- [ ] Create minimal service example showing Clean Architecture structure

## Phase 5: Create Templates

- [ ] Create `templates/application.properties` (sanitized)
- [ ] Create `templates/docker-compose.example.yml` (generic services)
- [ ] Create `templates/.env.example` (with placeholders only)

## Phase 6: CI/CD Examples

- [ ] Create `.github/workflows/examples/` directory
- [ ] Create generic build/test workflow example
- [ ] Create static analysis workflow example
- [ ] Remove all domain-specific steps
- [ ] Remove all real secrets/configurations

## Phase 7: Quality Checks

### Security Review
- [ ] No secrets or credentials in any files
- [ ] No real AWS resource names
- [ ] No real database connection strings
- [ ] No real service names (use generics)
- [ ] No domain-specific business logic

### Content Review
- [ ] All domain terms replaced with generics:
  - "candidate" → "user" or "entity"
  - "resume" → "document"
  - "job" → "resource" or "item"
  - "match" → "process" or "operation"
- [ ] All service names replaced with generics:
  - "candidate-service" → "actor-service"
  - "document-service" → "storage-service"
- [ ] All examples are generic and non-domain-specific

### Documentation Review
- [ ] All links work (update internal links)
- [ ] All code examples compile (or clearly marked as pseudocode)
- [ ] All architecture diagrams are generic
- [ ] All ADRs are sanitized

## Phase 8: Final Steps

- [ ] Add LICENSE file (commercial or chosen license)
- [ ] Add CONTRIBUTING.md (if accepting contributions)
- [ ] Add CODE_OF_CONDUCT.md (if accepting contributions)
- [ ] Set up repository description and topics
- [ ] Create initial commit with all sanitized content
- [ ] Push to public repository
- [ ] Update private repo README with link to public repo
- [ ] Consider adding a "Commercial License" badge

## Phase 9: Marketing

- [ ] Write blog post about the platform (if desired)
- [ ] Share on relevant communities (if desired)
- [ ] Create landing page or website (if desired)
- [ ] Set up contact method for licensing inquiries

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
| auth-service | identity-service, authentication-service |

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

## Quick Reference: What TO Include

- ✅ Interfaces and contracts
- ✅ Annotations
- ✅ Architecture documentation (sanitized)
- ✅ ADRs (sanitized)
- ✅ Example code (generic)
- ✅ Configuration templates
- ✅ CI/CD workflow examples
- ✅ Package structure standards
- ✅ Usage patterns and examples
