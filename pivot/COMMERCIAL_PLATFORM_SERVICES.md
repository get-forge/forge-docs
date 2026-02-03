# Commercial Platform Services Recommendations

This document identifies valuable, reusable services that would enhance a commercial platform based
on the current codebase architecture and common enterprise needs.

## Current Service Landscape

### Existing Services
- **auth-service** - Authentication, authorization, JWT token management
- **actor-service** - User/actor profile management (renamed from candidate-service)
- **document-service** - Document storage/parsing (being refactored to remove domain-specific code)

### Existing Infrastructure (Not Services)
- **Rate Limiting** - Bucket4j-based, in `libs/security` (library, not a service)
- **Caching** - Quarkus Cache, in `libs/cache` (library, not a service)
- **Metrics** - Micrometer + Prometheus (infrastructure)
- **Logging** - JSON console logs, correlation IDs, OpenTelemetry (infrastructure)
- **Health Checks** - SmallRye Health (library, not a service)
- **Service-to-Service Auth** - JWT-based, in `libs/security` (library, not a service)

## Recommended Services for Commercial Platform

### 1. Notification Service ⭐⭐⭐⭐⭐ **HIGH PRIORITY**

**Purpose:** Centralized notification delivery (email, SMS, push notifications)

**Why Valuable:**
- **Reusable across domains** - Every commercial platform needs notifications
- **Centralized management** - Single place for templates, delivery logic, retries
- **Provider abstraction** - Switch providers (SES, SendGrid, Twilio) without changing consumers
- **Compliance** - Centralized unsubscribe, opt-out, delivery tracking
- **Cost optimization** - Single integration point for bulk pricing, rate limiting

**Key Features:**
- Multi-channel support (email, SMS, push)
- Template management (HTML/text templates)
- Delivery tracking and retries
- Provider abstraction (AWS SES, SendGrid, Twilio, etc.)
- Rate limiting per channel/provider
- Unsubscribe/opt-out management
- Delivery status webhooks

**Architecture:**
- REST API for synchronous sends
- SQS integration for async/batch sends (when SQS is implemented)
- Template storage (S3 or database)
- Delivery status tracking (DynamoDB or PostgreSQL)

**Value Proposition:**
- Essential for any commercial platform
- Demonstrates production-grade messaging patterns
- Reusable across all domains
- Can be monetized as a platform feature

---

### 2. Audit/Event Logging Service ⭐⭐⭐⭐ **HIGH PRIORITY**

**Purpose:** Structured audit trail for compliance, security, and debugging

**Why Valuable:**
- **Compliance requirement** - Many industries require audit logs (SOC2, HIPAA, GDPR)
- **Security forensics** - Track who did what, when, from where
- **Debugging** - Structured event logs for troubleshooting
- **Analytics** - Event stream for business intelligence
- **Reusable** - Every service needs audit logging

**Key Features:**
- Structured event ingestion (REST API)
- Event schema validation
- Searchable event store (DynamoDB or Elasticsearch)
- Retention policies
- Event replay capabilities
- Integration with OpenTelemetry traces
- Real-time event streaming (optional)

**Event Types:**
- Authentication events (login, logout, token refresh)
- Authorization events (permission checks, access denials)
- Data access events (CRUD operations)
- Configuration changes
- Administrative actions

**Architecture:**
- REST API for event ingestion
- Event schema registry
- Storage: DynamoDB (hot) + S3 (cold/archive)
- Optional: EventBridge integration for real-time streaming
- Query API for event retrieval

**Value Proposition:**
- Essential for enterprise/commercial platforms
- Demonstrates compliance readiness
- Reusable across all services
- Can be monetized as enterprise feature

---

### 3. Webhook Service ⭐⭐⭐⭐ **HIGH PRIORITY**

**Purpose:** Outbound webhook delivery to external systems

**Why Valuable:**
- **Integration capability** - Essential for SaaS platforms
- **Event-driven architecture** - Decouple internal events from external integrations
- **Retry logic** - Handle external system failures gracefully
- **Security** - Sign webhooks, manage secrets
- **Reusable** - Every service that needs external integrations benefits

**Key Features:**
- Webhook subscription management (CRUD)
- Event filtering/routing
- Retry logic with exponential backoff
- Webhook signing (HMAC)
- Delivery status tracking
- Dead letter queue for failed deliveries
- Rate limiting per endpoint
- Webhook testing/sandbox mode

**Architecture:**
- REST API for subscription management
- SQS integration for async delivery (when SQS is implemented)
- Event filtering engine
- Retry queue with backoff
- Delivery status tracking (DynamoDB)
- Secret management (AWS Secrets Manager)

**Value Proposition:**
- Essential for SaaS platforms
- Demonstrates integration patterns
- Reusable across all services
- Can be monetized as integration feature

---

### 4. Feature Flags/Configuration Service ⭐⭐⭐ **MEDIUM PRIORITY**

**Purpose:** Dynamic feature toggles and runtime configuration

**Why Valuable:**
- **Feature rollout** - Gradual feature releases, A/B testing
- **Runtime configuration** - Change behavior without deployments
- **Emergency kill switches** - Disable features quickly
- **Reusable** - Every service benefits from feature flags

**Current State:**
- Basic toggle exists: `AugmentMatchesToggle` (in-memory, domain-specific)
- Feature detection framework exists: `FeatureRegistry`, `FeatureDetector` (for startup info)

**Key Features:**
- Feature flag CRUD API
- Per-user, per-service, per-environment flags
- Percentage rollouts (canary deployments)
- Flag evaluation caching
- Flag change notifications (optional)
- Integration with existing feature detection

**Architecture:**
- REST API for flag management
- Storage: DynamoDB or PostgreSQL
- Client library for flag evaluation
- Caching layer (Quarkus Cache or Redis)
- Optional: AWS AppConfig integration

**Value Proposition:**
- Demonstrates production-grade feature management
- Reusable across all services
- Can be monetized as platform feature
- Lower priority than notifications/audit/webhooks

---

### 5. Search Service ⭐⭐⭐ **MEDIUM PRIORITY** (Conditional)

**Purpose:** Full-text search across platform data

**Why Valuable:**
- **User experience** - Fast, relevant search results
- **Reusable** - Search across actors, documents, events, etc.
- **Scalable** - Dedicated search infrastructure

**When to Build:**
- Only if your platform needs search capabilities
- If you have searchable content (profiles, documents, events)
- If PostgreSQL full-text search is insufficient

**Key Features:**
- Index management (create, update, delete)
- Search API (query, filters, sorting, pagination)
- Search result ranking
- Faceted search
- Autocomplete/suggestions

**Architecture Options:**
- **AWS OpenSearch** - Managed Elasticsearch
- **PostgreSQL Full-Text Search** - Simpler, but less powerful
- **Algolia/Elasticsearch** - Third-party managed

**Value Proposition:**
- Only valuable if search is needed
- Can be deferred until search requirements emerge

---

## Services NOT Recommended (Infrastructure-Level)

### Log Shipping Service ❌

**Why Not a Service:**
- Log shipping is **infrastructure-level**, not application-level
- Use **CloudWatch Agent** or **Fluent Bit** (infrastructure tools)
- No business logic needed - just log forwarding
- Already handled by observability strategy (see ADR-0007)

**Recommendation:** Keep as infrastructure (CloudWatch Agent, Fluent Bit, ADOT Collector)

---

### Message Queue Service ❌

**Why Not a Service:**
- Message queues are **infrastructure** (AWS SQS, RabbitMQ, Kafka)
- Services **use** queues, they don't **implement** queues
- Your ADR-0008 already plans for SQS integration

**Recommendation:** Use AWS SQS when async messaging is needed (per ADR-0008)

---

## Implementation Priority

### Phase 1: Essential Services (Do First)
1. **Notification Service** - Every platform needs notifications
2. **Audit/Event Logging Service** - Compliance and security essential
3. **Webhook Service** - Essential for SaaS integrations

### Phase 2: Valuable Services (Do Second)
4. **Feature Flags/Configuration Service** - Valuable but not blocking

### Phase 3: Conditional Services (Do When Needed)
5. **Search Service** - Only if search is required

---

## Architecture Patterns to Follow

### Service Design Principles
- **Clean Architecture** - Follow existing package structure (domain, infrastructure, presentation)
- **REST API** - Synchronous REST for now (per ADR-0008), async-ready design
- **Service-to-Service Auth** - Use existing JWT-based auth from `libs/security`
- **Health Checks** - Use existing health check library from `libs/health`
- **Metrics** - Integrate with existing Micrometer/Prometheus setup
- **Circuit Breakers** - Use MicroProfile Fault Tolerance (already in use)

### Common Libraries to Reuse
- `libs/security` - Authentication, authorization, rate limiting
- `libs/health` - Health check base classes
- `libs/metrics` - Metrics collection patterns
- `libs/common` - Common utilities
- `libs/cache` - Caching patterns

### Storage Patterns
- **PostgreSQL** - For relational data (actors, configurations, audit logs)
- **DynamoDB** - For high-throughput, key-value data (events, webhook deliveries)
- **S3** - For object storage (templates, documents)

---

## Value Proposition Summary

### For Commercial Platform
1. **Notification Service** - Essential for user engagement, transactional emails
2. **Audit Service** - Essential for compliance, security, debugging
3. **Webhook Service** - Essential for SaaS integrations, event-driven architecture
4. **Feature Flags** - Valuable for feature management, gradual rollouts
5. **Search Service** - Conditional, only if search is needed

### For Public Repository (Free Components)
- **None of these services** should be in the public repo
- These are **commercial platform services**, not free components
- Public repo should focus on **libraries and contracts** (per `PUBLIC_REPO_STRATEGY.md`)

---

## Questions to Consider

1. **Notification Service:**
   - Which channels are priority? (Email first, then SMS/push?)
   - Do you need template management or just simple sends?
   - What providers? (AWS SES, SendGrid, Twilio?)

2. **Audit Service:**
   - What retention period? (90 days, 1 year, 7 years?)
   - What compliance requirements? (SOC2, HIPAA, GDPR?)
   - Real-time streaming needed or batch storage sufficient?

3. **Webhook Service:**
   - What events need webhooks? (User created, document uploaded, etc.)
   - What retry strategy? (Exponential backoff, max retries?)
   - What security model? (HMAC signing, API keys?)

4. **Feature Flags:**
   - Simple boolean flags or advanced (percentage rollouts, A/B testing)?
   - Per-user flags needed or just global flags?
   - Integration with AWS AppConfig or custom implementation?

---

## Next Steps

1. **Prioritize services** based on commercial platform needs
2. **Design service APIs** following existing patterns
3. **Implement Phase 1 services** (Notification, Audit, Webhook)
4. **Reuse existing libraries** (security, health, metrics, cache)
5. **Follow Clean Architecture** patterns already established
