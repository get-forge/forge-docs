# Caching Strategy

**Date:** 2025-01-27  
**Status:** Analysis & Implementation Plan  
**Context:** Application-level caching strategy for performance optimization and cost reduction

## Executive Summary

This document outlines a comprehensive caching strategy for the Bravo platform, validating use cases from the architectural improvements document and identifying additional opportunities. The strategy leverages Quarkus Cache with Caffeine (default) for local caching and plans for Redis/ElastiCache backend for production multi-instance deployments.

## Use Case Analysis

### Validated Use Cases (from ARCHITECTURAL_IMPROVEMENTS.md)

#### 1. Cognito Token Validation Results ✅ **HIGH PRIORITY**

**Current State:**
- Token validation occurs on every authenticated request via `CompositeTokenValidator`
- JWT parsing and signature validation via Cognito JWKS
- No caching of validation results

**Caching Opportunity:**
- **Cache Key:** JWT token string (or token hash + expiration time)
- **Cache Value:** `TokenValidationResult` (valid/invalid + user/service info)
- **TTL:** Token expiration time minus 60 seconds buffer (aligns with token lifetime)
- **Invalidation:** Automatic via TTL (tokens expire naturally)

**Impact:**
- **High frequency:** Every authenticated request validates tokens
- **Cost reduction:** Reduces Cognito JWKS lookups and JWT parsing overhead
- **Performance:** Eliminates cryptographic signature validation on cached tokens

**Implementation:**
```java
@CacheResult(cacheName = "token-validation", 
             keyGenerator = TokenCacheKeyGenerator.class)
public TokenValidationResult validateToken(String jwtToken) {
    // Existing validation logic
}
```

**Considerations:**
- Cache only valid tokens (let invalid tokens fail fast without caching)
- Use token expiration as TTL to ensure cache eviction matches token lifetime
- Handle token refresh scenarios (new token = new cache entry)

---

#### 2. User Profile Data ✅ **MEDIUM PRIORITY**

**Current State:**
- User profiles fetched from PostgreSQL via `CandidateRepository.findByCandidateId()`
- Called on every profile page load and header username display
- Profile data changes infrequently

**Caching Opportunity:**
- **Cache Key:** `candidateId` (UUID)
- **Cache Value:** `CandidateResponse` DTO
- **TTL:** 5-15 minutes (configurable)
- **Invalidation:** On profile update operations

**Impact:**
- **Frequency:** High (profile page, header username fetch)
- **Database load reduction:** Reduces PostgreSQL queries for frequently accessed profiles
- **Performance:** Sub-millisecond cache hits vs 10-50ms database queries

**Implementation:**
```java
@CacheResult(cacheName = "candidate-profiles")
public Optional<CandidateResponse> getProfile(String candidateId) {
    // Existing repository lookup
}

@CacheInvalidate(cacheName = "candidate-profiles")
public void updateProfile(String candidateId, UpdateRequest request) {
    // Profile update logic
}
```

**Considerations:**
- Cache invalidation on profile updates (registration, profile edits)
- Consider cache-aside pattern for optional resume data (separate cache)

---

#### 3. Parsed Document Results ✅ **MEDIUM PRIORITY**

**Current State:**
- Parsed resumes stored in DynamoDB (`RESUMES` table)
- Parsed job specs stored in DynamoDB (`JOBS` table)
- Documents fetched via `ParsedResumeRepository.findByCandidateId()` and `ParsedJobSpecRepository.findByTransactionId()`
- Documents are immutable once parsed (idempotent)

**Caching Opportunity:**
- **Cache Key:** `candidateId` (for resumes) or `transactionId` (for job specs)
- **Cache Value:** `ResumeResponse` or `JobSpecResponse` DTO
- **TTL:** 1 hour (documents rarely change after parsing)
- **Invalidation:** On document re-upload/re-parse

**Impact:**
- **Frequency:** Medium (document retrieval for matching, profile display)
- **DynamoDB cost reduction:** Reduces read capacity units (RCU) consumption
- **Performance:** Faster document retrieval for matching operations

**Implementation:**
```java
@CacheResult(cacheName = "parsed-resumes")
public Optional<ResumeResponse> getResume(String candidateId) {
    // Existing DynamoDB lookup
}

@CacheInvalidate(cacheName = "parsed-resumes")
public ResumeResponse uploadAndParseResume(String candidateId, FileUpload resume) {
    // Existing upload + parse logic
}
```

**Considerations:**
- Documents are idempotent (same input = same output), making caching safe
- Invalidate on re-upload (new document = new parse result)
- Consider larger TTL since documents rarely change

---

### Additional Use Cases Identified

#### 4. Cognito JWKS Keys ✅ **LOW PRIORITY** (Likely Already Cached)

**Current State:**
- JWKS keys fetched via `DefaultJWTParser` from Cognito `.well-known/jwks.json` endpoint
- Used for JWT signature validation

**Caching Opportunity:**
- **Cache Key:** JWKS URL (pool-specific)
- **Cache Value:** JWKS keyset
- **TTL:** 1 hour (JWKS keys rotate infrequently)

**Impact:**
- **Frequency:** Medium (during JWT validation, but keys are likely cached by library)
- **Verification Needed:** Check if `DefaultJWTParser` already caches JWKS internally

**Implementation:**
- Verify library behavior first
- If not cached, add explicit caching via Quarkus cache
- Lower priority since library likely handles this

---

#### 5. Service Token Caching ✅ **LOW PRIORITY** (Already Implemented)

**Current State:**
- `CachingServiceTokenProvider` already implements in-memory token caching
- Tokens cached with expiration tracking and automatic refresh

**Caching Opportunity:**
- **Enhancement:** Migrate to Quarkus Cache for consistency and Redis backend support
- **Current:** Custom in-memory cache with `ReentrantLock`
- **Future:** Quarkus cache with Redis backend for multi-instance deployments

**Impact:**
- **Consistency:** Aligns with application-wide caching strategy
- **Distributed:** Enables shared token cache across instances (if needed)

**Implementation:**
- Refactor `CachingServiceTokenProvider` to use `@CacheResult`
- Maintain existing refresh logic with cache TTL

---

#### 6. Resume/JobSpec Lookups by Transaction ID ✅ **LOW PRIORITY**

**Current State:**
- `ParsedResumeRepository.findByTransactionId()` and `ParsedJobSpecRepository.findByTransactionId()`
- Used for document retrieval by transaction ID

**Caching Opportunity:**
- **Cache Key:** `transactionId`
- **Cache Value:** `ResumeRecord` or `JobSpecRecord`
- **TTL:** 1 hour (documents immutable after parsing)

**Impact:**
- **Frequency:** Low (transaction ID lookups less common than candidate ID lookups)
- **Benefit:** Reduces DynamoDB reads for transaction-based lookups

**Implementation:**
- Lower priority than candidate ID lookups
- Can be added incrementally if transaction ID lookups become frequent

---

## Implementation Approach

### Phase 1: Local Caching with Quarkus Cache (Caffeine)

**Goal:** Implement caching using Quarkus Cache with default Caffeine backend for single-instance deployments and development.

**Steps:**

1. **Add Quarkus Cache Extension**
   ```xml
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-cache</artifactId>
   </dependency>
   ```

2. **Configure Cache Names and TTLs**
   ```properties
   # Token validation cache
   quarkus.cache.caffeine.token-validation.maximum-size=10000
   quarkus.cache.caffeine.token-validation.expire-after-write=1h
   
   # Candidate profile cache
   quarkus.cache.caffeine.candidate-profiles.maximum-size=5000
   quarkus.cache.caffeine.candidate-profiles.expire-after-write=10m
   
   # Parsed document cache
   quarkus.cache.caffeine.parsed-resumes.maximum-size=10000
   quarkus.cache.caffeine.parsed-resumes.expire-after-write=1h
   ```

3. **Implement Cache Annotations**
   - Add `@CacheResult` to read operations
   - Add `@CacheInvalidate` to write operations
   - Create custom `CacheKeyGenerator` for composite keys if needed

4. **Handle Cache Key Generation**
   - Token validation: Use token hash + expiration for key
   - Candidate profiles: Use `candidateId` directly
   - Documents: Use `candidateId` or `transactionId`

**Example Implementation:**

```java
@ApplicationScoped
public class CandidateService {
    
    @CacheResult(cacheName = "candidate-profiles")
    public Optional<CandidateResponse> getProfile(String candidateId) {
        // Existing implementation
    }
    
    @CacheInvalidate(cacheName = "candidate-profiles")
    public void register(RegisterRequestPartialAuth request) {
        // Existing implementation - invalidates cache on registration
    }
}
```

---

### Phase 2: Cache Metrics and Monitoring

**Goal:** Add metrics for cache performance and hit/miss rates before production migration.

**Rationale:**
- Understand cache performance characteristics before distributed migration
- Validate cache hit rates and TTL effectiveness
- Identify optimization opportunities

**Implementation:**
- Leverage Micrometer cache metrics (automatic with Quarkus Cache)
- Add custom metrics for cache hit/miss rates per cache name
- Visualize in Grafana "Bravo Infrastructure Metrics" dashboard

**Metrics to Track:**
- Cache hit rate (percentage)
- Cache miss rate (percentage)
- Cache eviction count
- Cache size (current entries)
- Cache operation latency

---

### Phase 3: Redis Backend for Production

**Goal:** Migrate to Redis/ElastiCache backend for distributed caching across multiple service instances.

**Rationale:**
- **Shared Cache:** Multiple service instances share the same cache
- **Consistency:** Cache invalidation works across all instances
- **Rate Limiting Synergy:** Redis already planned for distributed rate limiting (see ARCHITECTURAL_IMPROVEMENTS.md section 1.3)
- **Production Ready:** ElastiCache provides managed Redis with high availability

**Steps:**

1. **Add Redis Cache Extension**
   ```xml
   <dependency>
       <groupId>io.quarkus</groupId>
       <artifactId>quarkus-cache-redis</artifactId>
   </dependency>
   ```

2. **Configure Redis Connection**
   ```properties
   # Redis cache backend
   quarkus.cache.type=redis
   quarkus.redis.hosts=redis://elasticache-endpoint:6379
   
   # Cache-specific TTLs (same as Phase 1)
   quarkus.cache.redis.token-validation.ttl=1h
   quarkus.cache.redis.candidate-profiles.ttl=10m
   quarkus.cache.redis.parsed-resumes.ttl=1h
   ```

3. **Redis Infrastructure (AWS ElastiCache)**
   - Deploy ElastiCache Redis cluster (or replication group)
   - Configure security groups (VPC isolation)
   - Enable encryption in transit and at rest
   - Configure backup and snapshot policies

4. **Unified Redis Usage**
   - **Caching:** Application data caching (this document)
   - **Rate Limiting:** Distributed rate limiting buckets (future enhancement)
   - **Session Storage:** If needed for future features

**Benefits:**
- Single Redis cluster serves multiple purposes (caching + rate limiting)
- Reduced infrastructure complexity
- Cost optimization (shared cluster)

---

## Cache Key Strategy

### Token Validation Cache

**Key Format:** `token:${tokenHash}:${expirationTime}`

**Rationale:**
- Token hash prevents storing full tokens in cache (security)
- Expiration time ensures cache TTL matches token lifetime
- Composite key handles token refresh scenarios

**Implementation:**
```java
@ApplicationScoped
public class TokenCacheKeyGenerator implements CacheKeyGenerator {
    @Override
    public Object generate(Method method, Object... methodParams) {
        String token = (String) methodParams[0];
        // Extract expiration from token and create composite key
        return "token:" + hashToken(token) + ":" + extractExpiration(token);
    }
}
```

### Candidate Profile Cache

**Key Format:** `candidate:${candidateId}`

**Rationale:**
- Simple, direct key based on primary identifier
- UUID ensures uniqueness

### Document Cache

**Key Format:** `resume:${candidateId}` or `jobspec:${transactionId}`

**Rationale:**
- Candidate ID for resume lookups (most common)
- Transaction ID for transaction-based lookups

---

## Cache Invalidation Strategy

### Automatic Invalidation (TTL-based)

- **Token validation:** TTL = token expiration (automatic)
- **Candidate profiles:** TTL = 10 minutes (configurable)
- **Documents:** TTL = 1 hour (documents rarely change)

### Manual Invalidation (Write Operations)

- **Profile updates:** `@CacheInvalidate` on registration/profile update
- **Document re-upload:** `@CacheInvalidate` on new document upload
- **Token refresh:** New token = new cache entry (automatic via key)

### Cache Invalidation Patterns

1. **Write-Through:** Update cache on write (not recommended - adds complexity)
2. **Cache-Aside:** Invalidate on write, populate on read (recommended)
3. **Write-Behind:** Async cache updates (not needed for current use cases)

**Recommended:** Cache-Aside pattern with `@CacheInvalidate` on writes

---

## Error Handling and Fallback

### Cache Failures

**Strategy:** Fail open - if cache fails, fall back to underlying operation

**Implementation:**
- Quarkus Cache handles exceptions gracefully
- If cache operation fails, method executes normally
- Log cache failures for monitoring

**Example:**
```java
@CacheResult(cacheName = "candidate-profiles")
public Optional<CandidateResponse> getProfile(String candidateId) {
    // If cache fails, this still executes and returns result
    // Cache failure is logged but doesn't break the request
}
```

### Negative Caching

**Strategy:** Don't cache null/empty results (avoid cache stampede)

**Rationale:**
- Caching "not found" results can cause issues if data is added later
- Let "not found" queries hit the database (infrequent)

**Exception:** Token validation - cache invalid tokens briefly (5 minutes) to prevent repeated validation attempts

---

## Performance Considerations

### Cache Size Limits

- **Token validation:** 10,000 entries (high churn, short TTL)
- **Candidate profiles:** 5,000 entries (moderate churn)
- **Documents:** 10,000 entries (low churn, longer TTL)

### Memory Usage

- **Caffeine (local):** In-memory, bounded by `maximum-size`
- **Redis (distributed):** External memory, no local memory impact
- **Monitoring:** Track memory usage via JVM metrics

### Cache Warming (Optional)

**Strategy:** Pre-populate cache for frequently accessed data

**Implementation:**
- Not required initially
- Can be added if specific hot paths identified
- Consider for candidate profiles of active users

---

## Security Considerations

### Sensitive Data in Cache

**Token Validation:**
- Cache keys use token hashes (not full tokens)
- Cache values contain `TokenValidationResult` (user info, no secrets)

**Candidate Profiles:**
- Cache contains user profile data (PII)
- Ensure Redis encryption at rest and in transit
- Consider cache encryption for sensitive fields (if required by compliance)

**Documents:**
- Parsed documents may contain PII
- Same encryption requirements as candidate profiles

### Cache Key Collision Prevention

- Use namespaced keys: `token:`, `candidate:`, `resume:`
- Ensure unique identifiers (UUIDs for candidates, transaction IDs for documents)

---

## Integration with Rate Limiting

### Shared Redis Infrastructure

**Opportunity:** Use same Redis cluster for caching and rate limiting

**Benefits:**
- Single infrastructure component
- Reduced operational overhead
- Cost optimization

**Implementation:**
- Deploy ElastiCache Redis cluster
- Use different Redis databases (0 for caching, 1 for rate limiting) or key prefixes
- Configure separate connection pools if needed

**Key Prefixes:**
- Caching: `cache:token:`, `cache:candidate:`, `cache:resume:`
- Rate Limiting: `ratelimit:user:`, `ratelimit:service:`, `ratelimit:ip:`

---

## Implementation Roadmap

### Phase 1: Local Caching (Week 1-2)
- [ ] Add `quarkus-cache` dependency
- [ ] Configure cache names and TTLs
- [ ] Implement token validation caching
- [ ] Implement candidate profile caching
- [ ] Implement document caching
- [ ] Test cache hit/miss rates

### Phase 2: Metrics and Monitoring (Week 3)
- [ ] Add cache metrics (Micrometer integration)
- [ ] Create Grafana dashboard panels for cache metrics
- [ ] Monitor cache performance and hit rates
- [ ] Validate TTL effectiveness
- [ ] Identify optimization opportunities

### Phase 3: Redis Backend (Week 4-5)
- [ ] Add `quarkus-cache-redis` dependency
- [ ] Deploy ElastiCache Redis cluster (AWS)
- [ ] Configure Redis connection
- [ ] Migrate cache configuration to Redis
- [ ] Test distributed caching across instances
- [ ] Verify cache invalidation across instances

### Phase 4: Rate Limiting Integration (Week 6)
- [ ] Evaluate shared Redis for rate limiting
- [ ] Implement distributed rate limiting with Redis
- [ ] Test unified Redis infrastructure
- [ ] Document Redis usage patterns

---

## Success Metrics

### Performance Metrics
- Cache hit rate > 80% for token validation
- Cache hit rate > 60% for candidate profiles
- Cache hit rate > 70% for documents
- P95 latency reduction of 20-50ms for cached operations

### Cost Metrics
- DynamoDB read capacity reduction (20-30% for document lookups)
- PostgreSQL query reduction (30-40% for profile lookups)
- Cognito JWKS lookup reduction (80%+ for token validation)

### Operational Metrics
- Cache eviction rate (should be low for stable workloads)
- Cache failure rate (should be < 0.1%)
- Redis connection pool utilization

---

## References

- [Quarkus Cache Guide](https://quarkus.io/guides/cache)
- [Quarkus Redis Cache](https://quarkus.io/guides/cache-redis-reference)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [AWS ElastiCache for Redis](https://docs.aws.amazon.com/elasticache/latest/red-ug/)
- ARCHITECTURAL_IMPROVEMENTS.md section 4.3 (Caching Strategy)
- ARCHITECTURAL_IMPROVEMENTS.md section 1.3 (Rate Limiting - distributed Redis)

---

## Open Questions

1. **JWKS Caching:** Verify if `DefaultJWTParser` already caches JWKS keys internally
2. **Cache Encryption:** Determine if PII in cache requires additional encryption beyond Redis encryption
3. **Cache Warming:** Identify if specific hot paths benefit from cache warming
4. **Negative Caching:** Finalize strategy for caching "not found" results (currently: don't cache)

---

## Notes

- **Development:** Start with Caffeine (local) for simplicity
- **Production:** Migrate to Redis for distributed caching
- **Synergy:** Leverage Redis for both caching and rate limiting (shared infrastructure)
- **Incremental:** Implement use cases incrementally (token validation first, then profiles, then documents)

