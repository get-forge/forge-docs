# Free Components Sanitization Checklist

This document identifies domain-specific references in components being extracted to the public repository (per `PUBLIC_REPO_FREE_COMPONENTS.md`) that need sanitization.

## Components Being Extracted

### Tier 1: Health Checks ⭐⭐⭐⭐⭐
### Tier 2: Generic Utilities ⭐⭐⭐
### Tier 5: Rate Limiting Core ⭐⭐⭐⭐
### Tier 6: Method Entry Logging ⭐⭐⭐
### Tier 8: Validation Exception Mapper ⭐⭐⭐
### Tier 14: Additional Exception Mappers ⭐⭐⭐
### Tier 12: Taskfiles (Reference) ⭐⭐⭐⭐
### Tier 15: Static Analysis Configs ⭐⭐⭐
### Tier 17: Git Hooks & Dev Tooling ⭐⭐⭐

---

## Sanitization Required

### 1. Health Checks (`libs/health/`) ⚠️ **REQUIRES SANITIZATION**

#### `PostgresHealthCheck.java`
**File:** `libs/health/src/main/java/io/forge/health/infrastructure/PostgresHealthCheck.java`

**Domain-Specific References:**
- Line 26: `"bravo"` - Database name in example
- Line 26: `"candidates"` - Table name in example
- Line 39: `"bravo"` - Database name in example
- Line 39: `"candidates"` - Table name in example
- Line 36: `CandidateDatabaseHealthCheck` - Class name in example

**Sanitization Required:**
```java
// BEFORE (line 26):
return new PostgresHealthCheck(entityManager, "bravo", "candidates", "other_table") {};

// AFTER:
return new PostgresHealthCheck(entityManager, "mydb", "users", "orders") {};
```

```java
// BEFORE (line 36):
public class CandidateDatabaseHealthCheck extends PostgresHealthCheck {

// AFTER:
public class UserDatabaseHealthCheck extends PostgresHealthCheck {
```

```java
// BEFORE (line 39):
super(entityManager, "bravo", "candidates");

// AFTER:
super(entityManager, "mydb", "users");
```

**Status:** ⚠️ **MUST SANITIZE**

---

#### `DynamoDbHealthCheck.java`
**File:** `libs/health/src/main/java/io/forge/health/infrastructure/DynamoDbHealthCheck.java`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

#### `S3HealthCheck.java`
**File:** `libs/health/src/main/java/io/forge/health/infrastructure/S3HealthCheck.java`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

#### `CognitoHealthCheck.java`
**File:** `libs/health/src/main/java/io/forge/health/infrastructure/CognitoHealthCheck.java`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

### 2. Rate Limiting Core (`libs/security/infrastructure/throttle/`) ✅

#### `RateLimiter.java`
**File:** `libs/security/src/main/java/io/forge/security/infrastructure/throttle/RateLimiter.java`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

#### `RateLimitStatus.java`
**File:** `libs/security/src/main/java/io/forge/security/infrastructure/throttle/RateLimitStatus.java`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

#### `Bucket4jRateLimiter.java`
**File:** `libs/security/src/main/java/io/forge/security/infrastructure/throttle/Bucket4jRateLimiter.java`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

#### `RateLimiterProperties.java`
**File:** Check if exists

**Note:** Per `PUBLIC_REPO_FREE_COMPONENTS.md`, this interface should be extracted. Review for domain references.

---

### 3. Method Entry Logging (`libs/common/logging/`) ⚠️ **MINOR SANITIZATION**

#### `LogMethodEntry.java`
**File:** `libs/common/src/main/java/io/forge/common/logging/LogMethodEntry.java`

**Domain-Specific References:**
- Line 22: `ActorResponse` - Generic term, but could be more generic
- Line 23: `getProfile` - Generic method name ✅
- Line 33: `getProfile` - Generic method name ✅
- Line 49: `"for actor: %s"` - Uses "actor" which is generic ✅
- Line 81: `"for actor: %s"` - Uses "actor" which is generic ✅
- Line 84: `"for actor: %s"` - Uses "actor" which is generic ✅

**Assessment:**
- "Actor" is generic enough (could be user, entity, resource)
- Examples are already generic
- No recruitment-specific terms

**Status:** ✅ **NO SANITIZATION NEEDED** (actor is generic)

---

#### `LogMethodEntryInterceptor.java`
**File:** `libs/common/src/main/java/io/forge/common/logging/LogMethodEntryInterceptor.java`

**Domain-Specific References:** Check for any domain-specific logging messages

**Status:** ⚠️ **REVIEW NEEDED** (check implementation)

---

#### `LogMethodEntryParameterExtractor.java`
**File:** `libs/common/src/main/java/io/forge/common/logging/LogMethodEntryParameterExtractor.java`

**Domain-Specific References:** Check for any domain-specific logic

**Status:** ⚠️ **REVIEW NEEDED** (check implementation)

---

#### `LogMethodEntryReflectionUtils.java`
**File:** `libs/common/src/main/java/io/forge/common/logging/LogMethodEntryReflectionUtils.java`

**Domain-Specific References:** Check for any domain-specific logic

**Status:** ⚠️ **REVIEW NEEDED** (check implementation)

---

### 4. Validation Exception Mapper ✅

#### `ValidationExceptionMapper.java`
**File:** `libs/common/src/main/java/io/forge/common/validation/rest/ValidationExceptionMapper.java`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

### 5. Additional Exception Mappers ✅

#### `CircuitBreakerOpenExceptionMapper.java`
**File:** `libs/metrics/src/main/java/io/forge/metrics/presentation/rest/exception/CircuitBreakerOpenExceptionMapper.java`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

### 6. Generic Utilities (`libs/common/`) ⚠️ **REVIEW NEEDED**

#### `JsonNodeUtils.java`
**File:** `libs/common/src/main/java/io/forge/common/json/JsonNodeUtils.java`

**Status:** ⚠️ **REVIEW NEEDED** (check for domain-specific examples or logic)

---

#### `ValidationUtils.java`
**File:** `libs/common/src/main/java/io/forge/common/validation/ValidationUtils.java`

**Status:** ⚠️ **REVIEW NEEDED** (check for domain-specific examples or logic)

---

#### `Base64Utils.java`
**File:** `libs/common/src/main/java/io/forge/common/lang/Base64Utils.java`

**Status:** ⚠️ **REVIEW NEEDED** (check for domain-specific examples or logic)

---

#### `ClassUtils.java`
**File:** `libs/common/src/main/java/io/forge/common/lang/ClassUtils.java`

**Status:** ⚠️ **REVIEW NEEDED** (check for domain-specific examples or logic)

---

### 7. Taskfiles (Reference Material) ⚠️ **REQUIRES SANITIZATION**

#### `taskfile.yml`
**File:** `taskfile.yml`

**Domain-Specific References:**
- Line 22: `service:auth` - Generic ✅
- Line 29: `service:actor` - Generic ✅
- Line 37: `service:document` - Generic ✅
- Line 54: `app:candidate` - **DOMAIN-SPECIFIC** ❌
- Line 55: `Run backend candidate app` - **DOMAIN-SPECIFIC** ❌
- Line 59: `APP: backend-actor` - **DOMAIN-SPECIFIC** ❌
- Line 77: `ui:candidate` - **DOMAIN-SPECIFIC** ❌
- Line 78: `Run candidate web app` - **DOMAIN-SPECIFIC** ❌
- Line 82: `APP: web-actor` - **DOMAIN-SPECIFIC** ❌

**Sanitization Required:**
```yaml
# BEFORE:
app:candidate:
  desc: Run backend candidate app in Quarkus dev mode
  cmds:
    - task: _dev:application
      vars:
        APP: backend-actor

# AFTER:
app:example:
  desc: Run backend example app in Quarkus dev mode (example)
  cmds:
    - task: _dev:application
      vars:
        APP: backend-example  # Generic example
```

```yaml
# BEFORE:
ui:candidate:
  desc: Run actor web app in Quarkus dev mode
  cmds:
    - task: _dev:ui
      vars:
        APP: web-actor

# AFTER:
ui:example:
  desc: Run example web app in Quarkus dev mode (example)
  cmds:
    - task: _dev:ui
      vars:
        APP: web-example  # Generic example
```

**Status:** ⚠️ **MUST SANITIZE**

---

#### Other Taskfiles
**Files:** `.github/taskfile.build.yml`, `scripts/taskfile.*.yml`, `perf/taskfile.perf.yml`

**Status:** ⚠️ **REVIEW NEEDED** (check each taskfile for domain-specific references)

---

### 8. Static Analysis Configs ✅

#### `.config/.pmd-rules.xml`
**File:** `.config/.pmd-rules.xml`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

#### Checkstyle Configuration
**File:** Check if custom Checkstyle config exists (or uses Google checks)

**Status:** ⚠️ **REVIEW NEEDED** (if custom config exists)

---

### 9. Git Hooks & Dev Tooling ✅

#### `.config/lefthook.yml`
**File:** `.config/lefthook.yml`

**Domain-Specific References:** None found ✅

**Status:** ✅ **NO SANITIZATION NEEDED**

---

#### `renovate.json`
**File:** `renovate.json`

**Domain-Specific References:** Check for domain-specific package exclusions

**Status:** ⚠️ **REVIEW NEEDED** (check for `io.forge` or domain-specific exclusions)

---

### 10. Docker Compose (Reference) ⚠️ **REQUIRES SANITIZATION**

#### `compose.yml`
**File:** `compose.yml`

**Domain-Specific References:**
- Line 36: `POSTGRES_DB=bravo` - **DOMAIN-SPECIFIC** ❌

**Sanitization Required:**
```yaml
# BEFORE:
environment:
  - POSTGRES_DB=bravo

# AFTER:
environment:
  - POSTGRES_DB=mydb  # Generic database name
```

**Status:** ⚠️ **MUST SANITIZE**

---

## Summary

### ✅ No Sanitization Needed
- `DynamoDbHealthCheck.java`
- `S3HealthCheck.java`
- `CognitoHealthCheck.java`
- `RateLimiter.java`
- `RateLimitStatus.java`
- `Bucket4jRateLimiter.java`
- `ValidationExceptionMapper.java`
- `CircuitBreakerOpenExceptionMapper.java`
- `LogMethodEntry.java` (actor is generic)
- `.config/.pmd-rules.xml`
- `.config/lefthook.yml`

### ⚠️ Must Sanitize
1. **`PostgresHealthCheck.java`** - Replace "bravo" with "mydb", "candidates" with "users"
2. **`taskfile.yml`** - Replace "candidate" references with generic examples
3. **`compose.yml`** - Replace "bravo" database name with "mydb"

### ⚠️ Review Needed
1. **`LogMethodEntryInterceptor.java`** - Check implementation for domain references
2. **`LogMethodEntryParameterExtractor.java`** - Check implementation for domain references
3. **`LogMethodEntryReflectionUtils.java`** - Check implementation for domain references
4. **Generic Utilities** (`JsonNodeUtils`, `ValidationUtils`, `Base64Utils`, `ClassUtils`) - Review for domain-specific examples
5. **Other Taskfiles** - Review each for domain-specific references
6. **`renovate.json`** - Check for domain-specific package exclusions
7. **Checkstyle Configuration** - Review if custom config exists

---

## Action Items

### Before Extraction
1. ✅ Sanitize `PostgresHealthCheck.java` examples
2. ✅ Sanitize `taskfile.yml` domain-specific tasks
3. ✅ Sanitize `compose.yml` database name
4. ⚠️ Review logging interceptor implementations
5. ⚠️ Review generic utilities for domain examples
6. ⚠️ Review all taskfiles for domain references
7. ⚠️ Review `renovate.json` for domain-specific exclusions

### During Extraction
- Update package names from `io.forge` to public package (per `PUBLIC_REPO_FREE_COMPONENTS.md`)
- Update example comments to use generic terms
- Remove any domain-specific test data or examples

---

## Notes

- **"Actor" is generic** - No need to change "actor" references in `LogMethodEntry.java` examples
- **Package names** - Will be changed during extraction (per strategy doc)
- **Infrastructure scripts** - Not being extracted, so no sanitization needed for `scripts/` directory
- **Service names** - Generic service names (auth, actor, document) are fine for examples
