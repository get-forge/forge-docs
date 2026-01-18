# Public Repository Strategy

Complete strategy for what to expose in the public repository, component analysis, dependency approach, and implementation guidance.

---

## 🎯 Strategy Overview

**Goal**: Demonstrate technical architecture and operational maturity while protecting core business logic and implementations.

**Approach**: Expose **contracts, examples, reference material, and quality indicators** that showcase sophistication without revealing implementation details.

---

## 📊 Component Analysis (20 Tiers)

### **Tier 1: Health Checks** ⭐⭐⭐⭐⭐ ✅ **COMPLETE** ✅ **MIGRATED**
- **Status**: STRONGLY RECOMMEND
- **Value**: Very High | **Complexity**: Low | **Risk**: Zero
- **What**: PostgresHealthCheck, DynamoDbHealthCheck, S3HealthCheck, CognitoHealthCheck
- **Why**: Production-ready, generic, zero risk, high value
- **Location**: `forge-kit/forge-contracts/forge-health` (published as `io.forge:forge-health`)

### **Tier 1: Metrics Framework** ⭐⭐⭐⭐⭐ ✅ **COMPLETE** ✅ **MIGRATED**
- **Status**: STRONGLY RECOMMEND
- **Value**: Very High | **Complexity**: Low | **Risk**: Zero
- **What**: MetricsRecorder, ServiceMetrics, ServiceMetricsInterceptor, CircuitBreakerMetrics, DatabaseMetricsInterceptor, DatabaseMetrics etc.
- **Why**: Production-ready, generic, zero risk, high value
- **Location**: `forge-kit/forge-contracts/forge-metrics` (published as `io.forge:forge-metrics`)

### **Tier 2: Generic Utilities** ⭐⭐⭐ ✅ **MIGRATED**
- **Status**: Optional
- **Value**: Medium | **Complexity**: Low | **Risk**: Low
- **What**: Base64Utils, ClassUtils, JWT utilities
- **Why**: Useful but not critical
- **Location**: `forge-kit/forge-contracts/forge-common` (published as `io.forge:forge-common`)

### **Tier 3: Demo Implementations** ⭐⭐
- **Status**: Skip (or do later)
- **Value**: Low | **Complexity**: Medium | **Risk**: Low
- **What**: Simplified examples showing patterns
- **Why**: Documentation might be sufficient

### **Tier 4: Config Templates** ⭐
- **Status**: Include
- **Value**: Low | **Complexity**: Very Low | **Risk**: Zero
- **What**: application.properties.template, docker-compose.example.yml
- **Why**: Very low effort, helpful for documentation

### **Tier 5: Rate Limiting Core** ⭐⭐⭐⭐ ✅ **COMPLETE** ✅ **MIGRATED**
- **Status**: STRONGLY CONSIDER
- **Value**: High | **Complexity**: Medium | **Risk**: Low
- **What**: RateLimiter interface, RateLimitStatus, Bucket4jRateLimiter, key resolvers
- **Why**: High value but requires careful extraction
- **Location**: `forge-kit/forge-contracts/forge-throttle` (published as `io.forge:forge-throttle`)

### **Tier 6: Method Entry Logging** ⭐⭐⭐ ✅ **MIGRATED**
- **Status**: RECOMMEND
- **Value**: Medium | **Complexity**: Low | **Risk**: Zero
- **What**: @LogMethodEntry annotation, interceptor, utilities
- **Why**: Easy win, zero risk, useful utility
- **Location**: `forge-kit/forge-contracts/forge-common` (published as `io.forge:forge-common`)
- **Note**: Codebase uses `io.forge.kit.common.logging.LogMethodEntry`

### **Tier 7: Caching Key Generators** ⭐⭐
- **Status**: SKIP
- **Value**: Low | **Complexity**: High | **Risk**: Medium
- **What**: Cache key generation utilities
- **Why**: Too domain-specific, lower value

### **Tier 8: Validation Exception Mapper** ⭐⭐⭐ ✅ **MIGRATED**
- **Status**: RECOMMEND
- **Value**: Medium | **Complexity**: Very Low | **Risk**: Zero
- **What**: ValidationExceptionMapper for Jakarta Bean Validation
- **Why**: Very easy, zero risk, useful utility
- **Location**: `forge-kit/forge-contracts/forge-common` (published as `io.forge:forge-common`)

### **Tier 9: Startup Info Panel** ⭐⭐⭐
- **Status**: CONSIDER
- **Value**: Medium | **Complexity**: Medium | **Risk**: Low
- **What**: Feature detection framework, startup info display
- **Why**: Good DX but Quarkus-specific

### **Tier 10: Graceful Shutdown Handler** ⭐⭐⭐
- **Status**: OPTIONAL (Remaining - Not Migrated)
- **Value**: Medium | **Complexity**: Low | **Risk**: Low
- **What**: Shutdown lifecycle management (`ShutdownLifecycleEventHandler`)
- **Why**: Good pattern but Quarkus-specific. User indicated this may be the only remaining useful component, but it's optional.

### **Tier 11: Test Utilities** ⭐⭐ ✅ **MIGRATED**
- **Status**: Optional
- **Value**: Low | **Complexity**: Very Low | **Risk**: Zero
- **What**: QuarkusPortsEnvTestResource, test helpers
- **Why**: Low value but also low effort
- **Location**: `forge-kit/forge-contracts/forge-common` (published as `io.forge:forge-common`)
- **Note**: Codebase uses `io.forge.kit.common.test.QuarkusPortsEnvTestResource`

### **Tier 12: Taskfiles (Build/Deploy Control Plane)** ⭐⭐⭐⭐ ✅ **MIGRATED**
- **Status**: STRONGLY RECOMMEND
- **Value**: Very High | **Complexity**: Low-Medium | **Risk**: Low
- **What**: All Taskfile.yml files (sanitized as reference)
- **Why**: Excellent operational maturity demonstration
- **Location**: `forge-kit/taskfile.yml`, `forge-kit/scripts/taskfile.init.yml`

### **Tier 13: Code Quality & Test Coverage Metrics** ⭐⭐⭐⭐⭐
- **Status**: STRONGLY RECOMMEND
- **Value**: Very High | **Complexity**: Very Low | **Risk**: Zero
- **What**: CI/CD badges, Codecov integration, coverage reports
- **Why**: Zero effort, very high value, builds trust immediately

### **Tier 14: Additional Exception Mappers** ⭐⭐⭐ ✅ **MIGRATED**
- **Status**: RECOMMEND
- **Value**: Medium | **Complexity**: Very Low | **Risk**: Zero
- **What**: CircuitBreakerOpenExceptionMapper
- **Why**: Very easy, zero risk, useful utility
- **Location**: `forge-kit/forge-contracts/forge-metrics/src/main/java/io/forge/kit/metrics/domain/exception/CircuitBreakerOpenExceptionMapper.java`

### **Tier 15: Static Analysis Configurations** ⭐⭐⭐ ✅ **MIGRATED** (Copied)
- **Status**: RECOMMEND
- **Value**: Medium | **Complexity**: Very Low | **Risk**: Zero
- **What**: PMD rules, Checkstyle config, SpotBugs configs
- **Why**: Very low effort, zero risk, useful reference
- **Location**: `forge-kit/.config/` (copied by necessity - Maven POMs can't be extracted)
- **Note**: Config files are copied to both repositories as they're referenced by Maven POMs

### **Tier 16: Code Formatting Configurations** ⭐⭐ ✅ **MIGRATED** (Copied)
- **Status**: Optional
- **Value**: Low | **Complexity**: Very Low | **Risk**: Zero
- **What**: Spotless config, Eclipse formatter, linting configs
- **Why**: Low value but also zero effort
- **Location**: `forge-kit/.config/` (copied by necessity - Maven POMs can't be extracted)
- **Note**: Config files are copied to both repositories as they're referenced by Maven POMs

### **Tier 17: Git Hooks & Dev Tooling** ⭐⭐⭐ ✅ **MIGRATED**
- **Status**: RECOMMEND
- **Value**: Medium | **Complexity**: Very Low | **Risk**: Zero
- **What**: Renovate config
- **Why**: Very low effort, zero risk, shows DX focus
- **Location**: `forge-kit/renovate.json`

### **Tier 18: Docker Compose & Local Development** ⭐⭐⭐
- **Status**: CONSIDER
- **Value**: Medium | **Complexity**: Low | **Risk**: Low
- **What**: compose.yml, Docker service scripts
- **Why**: Good reference but requires sanitization

### **Tier 19: Maven Build Configuration Patterns** ⭐⭐ ✅ **MIGRATED** (Copied)
- **Status**: Optional
- **Value**: Low | **Complexity**: Medium | **Risk**: Low
- **What**: Build profiles, plugin configurations (sanitized)
- **Why**: Lower value, requires sanitization
- **Location**: `forge-kit/` (copied by necessity - Maven POMs can't be extracted)
- **Note**: Maven POM configurations are copied to both repositories as they can't be extracted as dependencies

### **Tier 20: Performance Testing Framework (k6)** ⭐⭐
- **Status**: SKIP
- **Value**: Low | **Complexity**: High | **Risk**: Medium
- **What**: k6 test scenarios and flows
- **Why**: Too domain-specific, would need generic examples

---

## 🎯 Recommended Implementation Order

### **Phase 1: Immediate Wins (Do First)**
1. ⏳ **Code Quality Badges** (Tier 13) - **NEXT: HIGHEST ROI** - Set up immediately, zero effort, very high value
2. ✅ **Health Checks** (Tier 1) - **COMPLETE** ✅ **MIGRATED** - Highest value code component
3. ⏳ **Validation Exception Mapper** (Tier 8) - **NEXT: HIGH ROI** - Very easy, zero risk, medium value
4. ✅ **Circuit Breaker Exception Mapper** (Tier 14) - ✅ **MIGRATED** - Very easy, zero risk
5. ⏳ **Method Entry Logging** (Tier 6) - Easy win, zero risk, medium value
6. ⏳ **Static Analysis Configs** (Tier 15) - **NEXT: HIGH ROI** - Very easy, zero risk, medium value
7. ✅ **Git Hooks Config** (Tier 17) - ✅ **MIGRATED** - Very easy, zero risk
8. ✅ **Taskfiles** (Tier 12) - ✅ **MIGRATED** - Excellent reference material

### **Phase 2: High-Value Components**
9. ✅ **Rate Limiting Core** (Tier 5) - ✅ **MIGRATED** - High value but requires careful extraction
10. ✅ **Metrics Framework** (Tier 1) - ✅ **MIGRATED** - Production-ready observability
11. ✅ **Generic Utilities** (Tier 2) - ✅ **MIGRATED** - Common utilities

### **Phase 3: Developer Experience (Optional)**
10. 🤔 **Docker Compose** (Tier 18) - Good reference, requires sanitization
11. 🤔 **Startup Info Panel** (Tier 9) - Good DX but Quarkus-specific
12. 🤔 **Graceful Shutdown** (Tier 10) - **OPTIONAL** - Good pattern but Quarkus-specific. User indicated this may be the only remaining useful component, but it's optional.

### **Phase 4: Nice to Have**
13. 🤔 **Code Formatting Configs** (Tier 16) - Optional, zero effort
14. 🤔 **Generic Utilities** (Tier 2) - Optional, lower priority
15. 🤔 **Maven Build Patterns** (Tier 19) - Optional, requires sanitization
16. 🤔 **Test Utilities** (Tier 11) - Optional, low value
17. ✅ **Config Templates** (Tier 4) - Include as documentation

---

## 📦 Dependency Strategy

### **Real Maven Dependencies** (Publish as Artifacts)

**What**: Annotations and pure interfaces that define contracts

**Why**:
- Maximum value demonstration - people can actually use them
- Shows you're serious about the platform
- Creates a "try before you buy" experience
- Builds trust through usable code
- Can be published to Maven Central or GitHub Packages

**Examples**:
- `@Secured`, `@AllowedServices`, `@ServiceMetrics` annotations
- `TokenValidator`, `MetricsRecorder`, `RateLimiter` interfaces
- Health check base classes (already published)

**Structure**:
```
public-repo/
├── contracts/
│   ├── security-contracts/
│   │   ├── pom.xml
│   │   └── src/main/java/tech/eagledrive/security/contracts/
│   └── metrics-contracts/
│       ├── pom.xml
│       └── src/main/java/tech/eagledrive/metrics/contracts/
```

**Key Points**:
- **No implementation dependencies** - these are pure contracts
- **Minimal dependencies** - only what's needed for annotations (Jakarta EE)
- **Separate package names** - `tech.eagledrive.security.contracts` vs `tech.eagledrive.security` (private)
- **Can be published** to Maven Central or GitHub Packages
- **Version independently** from private repo

### **Reference Only** (Documentation/Examples)

**What**: Example code, documentation, ADRs, architecture docs

**Why**:
- No maintenance burden
- No versioning complexity
- Clear demonstration without commitment
- Can evolve independently

**Examples**:
- Example resources showing usage
- Architecture documentation
- ADRs
- Configuration templates
- CI/CD workflow examples
- Taskfiles (as reference material)

---

## ✅ Safe to Expose

### **1. Annotation Interfaces**
- `@Secured` - Authentication annotation
- `@AllowedServices` - Service-level authorization annotation
- `@ServiceMetrics` - Metrics collection annotation

**Why safe**: Annotations are just metadata. The value is in the implementation, which stays private.

### **2. Domain Interfaces** (Architectural Contracts)
- `TokenValidator` - JWT token validation contract
- `MetricsRecorder` - Metrics recording contract
- `RateLimiter` - Rate limiting contract
- `ServiceAuthenticationProvider` - Service auth contract
- `ServiceTokenProvider` - Service token management contract
- `MetricsResultIndicator` - Metrics result marker interface

**Why safe**: Interfaces define contracts, not implementations. Shows architectural sophistication.

### **3. Example/Demo Resources** (Sanitized)
Minimal examples that demonstrate usage patterns:
- How to use `@Secured`
- How to use `@AllowedServices`
- How to use `@ServiceMetrics`
- Service-to-service communication patterns

**Why safe**: Examples show patterns, not business logic. Can be generic.

### **4. Architecture Decision Records (ADRs)** (Sanitized)
**Safe to expose**:
- ADR-0012: Clean Architecture Package Structure
- ADR-0011: Stateless JWT Authentication (remove Cognito-specific details)
- ADR-0007: Observability Strategy
- ADR-0014: Application Caching Strategy
- ADR-0010: REST API Design Standards

**Sanitize**: Remove references to "recruitment", "candidate", "job", "resume". Replace with generic terms: "entity", "resource", "document".

### **5. Architecture Documentation** (Sanitized)
**Safe to expose** (with sanitization):
- `AUTHENTICATION.md` - Remove Cognito-specific details, keep patterns
- `SERVICE_TO_SERVICE_AUTH.md` - Excellent for showing security architecture
- `HEALTH_CHECKS_IMPLEMENTATION.md` - Shows operational maturity
- `CACHING_STRATEGY.md` - Shows performance thinking
- `METRICS_IMPLEMENTATION_STATUS.md` - Shows observability maturity

### **6. Configuration Examples** (Sanitized Templates)
- Quarkus `application.properties` templates
- Docker Compose examples (generic services)
- Environment variable templates

### **7. CI/CD Workflow Examples** (Generic Patterns)
- Build/test workflow (remove domain-specific steps)
- Static analysis workflow
- Deployment patterns (generic, no secrets)

---

## ❌ Do NOT Expose

### **1. Full Service Implementations**
- All code in `services/` directory
- All code in `applications/` directory
- Repository implementations
- Service implementations
- Business logic

### **2. Infrastructure Implementations**
- Actual interceptor implementations
- Filter implementations
- Token validator implementations
- Metrics recorder implementations
- Rate limiter implementations

### **3. Domain-Specific Code**
- Any code referencing "candidate", "resume", "job", "match"
- Business domain models
- Domain services
- Domain repositories

### **4. Configuration Files**
- Real `application.properties` files
- Real environment configurations
- Secrets or credentials (even templates with real structure)
- AWS resource names
- Database schemas

### **5. Complete Library Implementations**
- Keep library implementations private
- Only expose interfaces and annotations

### **6. Deployment Scripts**
- Actual deployment scripts with real resource names
- AWS setup scripts with real configurations
- Database migration scripts

---

## 📦 What You're Offering

### **Real, Usable Code** (Maven Artifacts)
- ✅ Health Checks (production-ready) - **COMPLETE** ✅ **MIGRATED** (`io.forge:forge-health`)
- ✅ Metrics Framework - ✅ **MIGRATED** (`io.forge:forge-metrics`)
- ✅ Rate Limiting Core - ✅ **MIGRATED** (`io.forge:forge-throttle`)
- ✅ Generic Utilities - ✅ **MIGRATED** (`io.forge:forge-common`)
- ✅ Circuit Breaker Exception Mapper - ✅ **MIGRATED** (in `forge-metrics`)
- Validation Exception Mapper
- Method Entry Logging

### **Contracts & Interfaces** (Maven Artifacts)
- @Secured, @AllowedServices, @ServiceMetrics annotations
- TokenValidator, MetricsRecorder, RateLimiter interfaces
- Domain contracts

### **Reference Material** (Examples/Templates)
- Taskfiles (build/deploy control plane)
- Static analysis configurations
- Git hooks configurations
- Docker Compose setup
- Code formatting configurations
- Maven build patterns

### **Quality Indicators** (Badges/Metrics)
- CI/CD workflow badges
- Code coverage badges and reports
- Static analysis results
- Code quality metrics

### **Documentation** (Sanitized)
- Architecture Decision Records (ADRs)
- Architecture documentation
- Library READMEs
- Development guides

---

## 💰 Value Proposition

Your public repository demonstrates:

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
   - Build/deploy control plane

4. **Developer Experience**
   - Simple annotations for complex features
   - Clear contracts and interfaces
   - Well-documented patterns
   - Developer tooling

5. **Production Readiness**
   - Fault tolerance patterns
   - Caching strategies
   - CI/CD workflows
   - Code quality gates
   - Test coverage tracking

6. **Code Quality**
   - 80%+ test coverage
   - All static analysis passing
   - Complexity metrics
   - Automated quality gates

---

## 📋 Implementation Checklist

### Phase 1: Preparation
- [x] Health Checks (Tier 1) - **COMPLETE** ✅ **MIGRATED**
- [ ] Code Quality Badges (Tier 13) - **NEXT: HIGHEST ROI** (zero effort, very high value)
- [x] Validation Exception Mapper (Tier 8) - ✅ **MIGRATED**
- [x] Circuit Breaker Exception Mapper (Tier 14) - ✅ **MIGRATED**
- [x] Method Entry Logging (Tier 6) - ✅ **MIGRATED**
- [x] Static Analysis Configs (Tier 15) - ✅ **MIGRATED** (Copied)
- [x] Code Formatting Configs (Tier 16) - ✅ **MIGRATED** (Copied)
- [x] Git Hooks Config (Tier 17) - ✅ **MIGRATED** (Renovate)
- [x] Taskfiles (Tier 12) - ✅ **MIGRATED**
- [x] Test Utilities (Tier 11) - ✅ **MIGRATED**
- [x] Maven Build Configuration Patterns (Tier 19) - ✅ **MIGRATED** (Copied)
- [ ] Graceful Shutdown Handler (Tier 10) - **OPTIONAL** - User indicated this may be the only remaining useful component

### Phase 2: High-Value Components
- [x] Rate Limiting Core (Tier 5) - ✅ **MIGRATED**
- [x] Metrics Framework (Tier 1) - ✅ **MIGRATED**
- [x] Generic Utilities (Tier 2) - ✅ **MIGRATED**

### Phase 3: Contracts & Interfaces
- [ ] Extract `@Secured` annotation to `contracts/security/`
- [ ] Extract `@AllowedServices` annotation to `contracts/security/`
- [ ] Extract `TokenValidator` interface to `contracts/security/`
- [ ] Extract `RateLimiter` interface to `contracts/security/`
- [ ] Extract `@ServiceMetrics` annotation to `contracts/metrics/`
- [ ] Extract `MetricsRecorder` interface to `contracts/metrics/`

### Phase 4: Documentation
- [ ] Sanitize ADRs (remove domain references)
- [ ] Sanitize architecture documentation
- [ ] Create example resources
- [ ] Create configuration templates

### Phase 5: Publishing
- [ ] Set up Maven publishing (Maven Central or GitHub Packages)
- [ ] Publish health checks (already done)
- [ ] Publish contracts when ready
- [ ] Set up CI/CD for public repo
- [ ] Set up Codecov for public repo

---

## 📚 Related Documents

- **[SANITIZATION_REPORT.md](SANITIZATION_REPORT.md)** - Domain-specific references that need sanitization
- **[COMMERCIAL_PLATFORM_SERVICES.md](COMMERCIAL_PLATFORM_SERVICES.md)** - Recommendations for commercial platform services
- **[FREE_COMPONENTS_SANITIZATION_CHECKLIST.md](FREE_COMPONENTS_SANITIZATION_CHECKLIST.md)** - Detailed sanitization checklist for components

---

## 🎉 Summary

You have **20 tiers of components** analyzed, with clear recommendations for each:

- **6 STRONGLY RECOMMEND** (do these first)
- **4 RECOMMEND** (easy wins)
- **3 CONSIDER** (good but optional)
- **7 Optional/Skip** (lower priority or too domain-specific)

**Status**: 
- ✅ **MIGRATED**: Health Checks (Tier 1), Metrics Framework (Tier 1), Generic Utilities (Tier 2), Rate Limiting Core (Tier 5), Method Entry Logging (Tier 6), Validation Exception Mapper (Tier 8), Test Utilities (Tier 11), Taskfiles (Tier 12), Circuit Breaker Exception Mapper (Tier 14), Static Analysis Configurations (Tier 15 - Copied), Code Formatting Configurations (Tier 16 - Copied), Git Hooks Config (Tier 17), Maven Build Configuration Patterns (Tier 19 - Copied)
- ✅ **COMMON MODULE MIGRATIONS COMPLETE**: All useful common modules have been extracted to the public repository. Config files (Tiers 15, 16, 19) are copied by necessity as Maven POMs can't be extracted. Remaining items are either domain-specific, not particularly useful, or optional (e.g., ShutdownHandler - Tier 10).
- **Next Steps**: Focus on high-value items: Code Quality Badges (Tier 13), then move to Contracts & Interfaces (Phase 3) and Documentation (Phase 4).

The strategy balances **demonstrating value** with **protecting your core platform**, giving you a clear path to monetization while building trust through transparency.

---

## 🎯 Bang for Buck: Recommended Next Steps

With common module migrations complete, here's the recommended order for maximum impact:

### **Immediate High-ROI Items (Do These First)**

1. **Code Quality Badges (Tier 13)** ⭐⭐⭐⭐⭐
   - **Effort**: Zero (just configure CI/CD badges)
   - **Value**: Very High (builds trust immediately)
   - **Impact**: Shows production readiness at a glance
   - **Time**: 15-30 minutes
   - **Status**: ⏳ **NEXT** - Only remaining Phase 1 item

### **High-Value Strategic Items (Next Phase)**

4. **Contracts & Interfaces (Phase 3)** ⭐⭐⭐⭐
   - **Effort**: Medium (extract annotations/interfaces, create examples)
   - **Value**: Very High (demonstrates architectural sophistication)
   - **Impact**: Shows you're serious about the platform
   - **Time**: 4-8 hours
   - **What**: Extract `@Secured`, `@AllowedServices`, `@ServiceMetrics`, `TokenValidator`, `RateLimiter`, `MetricsRecorder` to contracts package

5. **Documentation Sanitization (Phase 4)** ⭐⭐⭐
   - **Effort**: Medium (review and sanitize docs)
   - **Value**: High (demonstrates thought leadership)
   - **Impact**: Shows architectural maturity and decision-making process
   - **Time**: 8-16 hours
   - **What**: Sanitize ADRs, architecture docs, create examples

### **Optional Items (Lower Priority)**

6. **Method Entry Logging (Tier 6)** ⭐⭐⭐
   - **Effort**: Low (extract annotation and interceptor)
   - **Value**: Medium (useful utility)
   - **Time**: 2-4 hours

7. **Graceful Shutdown Handler (Tier 10)** ⭐⭐⭐
   - **Effort**: Low (extract class)
   - **Value**: Medium (good pattern, but Quarkus-specific)
   - **Time**: 1-2 hours
   - **Note**: User indicated this may be the only remaining useful component, but it's optional

### **Recommended Order**

**Week 1 (Quick Wins):**
1. Code Quality Badges (30 min)
2. Static Analysis Configs (2 hours)
3. Validation Exception Mapper (1 hour)

**Week 2-3 (Strategic Value):**
4. Contracts & Interfaces (8 hours)
5. Documentation Sanitization (16 hours)

**Optional (If Time Permits):**
6. Method Entry Logging (4 hours)
7. Graceful Shutdown Handler (2 hours)

**Total Estimated Time**: ~35 hours for core items, ~41 hours including optional items.
