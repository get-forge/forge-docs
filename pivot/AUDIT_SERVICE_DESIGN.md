# Audit Service Design

**Status:** Phase 1 complete (local with Postgres).
See `libs/audit`, `services/audit-service`, and `@AuditEvent` usages in auth-service and
notification-service for current implementation.

Cross-cutting, annotation-driven audit logging for Quarkus microservices.
Structured events are emitted and, in production, will flow via AWS EventBridge to a central audit
pipeline (aligned with [centralized alerting][audit-centralized-alerting]).
Minimal business-logic impact: annotate methods; an interceptor and publisher handle capture and delivery.

[audit-centralized-alerting]: https://awsfundamentals.com/blog/build-centralized-alerting-across-your-organization-with-cloudwatch-eventbridge-lambda-and-cdk

---

## Design checklist

- [x] Annotation, interceptor, and event contract (library API).
- [x] Publisher and ingest API (direct HTTP to audit-service).
- [x] Pluggable dispatchers (HTTP today; no-op/eventbridge configurable; EventBridge impl later).
- [ ] Audit-service EventBridge consumer and optional observability account.
- [x] Align with existing patterns (LogMethodEntry in forge-kit, notification fire-and-forget).

---

## Current implementation (summary)

- **libs/audit:** `@AuditEvent`, enums, interceptor (audit *after* `proceed()`), `AuditEventRequestBuilder`,
  actorId resolution chain (`io.forge.audit.auth`), publisher.
  Publisher delegates to an `AuditEventDispatcher` produced from `audit.dispatcher.type`
  (HTTP, no-op, or eventbridge placeholder); HTTP dispatcher wraps `AuditServiceClient`;
  send is async (fire-and-forget) with context/classloader propagation.
- **libs/domain-dtos / domain-clients:** `AuditEventRequest`; `AuditServiceClient` (POST /audit/events).
- **services/audit-service:** Postgres `audit.audit_events`, Flyway V1, `AuditResource` with
  `@AllowedServices({"auth-service", "notification-service"})`.
- **Emitting services:** Configure `quarkus.rest-client.AuditServiceClient.url`; annotate methods with
  `@AuditEvent` (e.g. login, register, sendWelcomeEmail, sendPasswordResetEmail).

**Run locally:** Postgres + audit-service (port from `AUDIT_SERVICE_PORT`) + auth/notification-service
with that env set.
No record persisted → ensure audit-service is running and URL is set; check logs for
"Failed to send audit event" or non-2xx ingest response.

---

## Overview

**Purpose:** Structured, centralized audit for compliance (e.g. SOC2, HIPAA, GDPR), security
traceability, and operational observability, without embedding logging in business logic.

**Pattern:** Same style as `@LogMethodEntry` in forge-kit: annotate methods; CDI interceptor runs after
the method, builds a structured record, resolves actorId (request context or
AuthResponse/RegistrationResponse for login/register), and calls the publisher.
Publisher POSTs to audit-service (today) or will dispatch to EventBridge (future).

**Outcomes:** Annotations only, structured events, extensible storage, path to EventBridge and
centralized alerting.

---

## Architecture (high level)

| Component   | Location        | Role |
|------------|-----------------|------|
| Audit lib  | `libs/audit`    | Annotation, interceptor, request builder, actorId resolvers, publisher. |
| Wire DTO    | `libs/domain-dtos` | `AuditEventRequest`. |
| REST client | `libs/domain-clients` | `AuditServiceClient`. |
| Audit service | `services/audit-service` | REST ingest, Postgres persistence, health. |

**Flow:** Method with `@AuditEvent` → interceptor runs after `proceed()` → build request
(argPaths via forge-kit extractor, actorId via resolver chain) → publisher POSTs to audit-service
→ 201 and persist.

---

## EventBridge (future)

When EventBridge is introduced, the emitting service will call **PutEvents** instead of audit-service
directly; the interceptor and publisher stay the same; only the dispatcher implementation changes
(e.g. `audit.dispatcher.type=eventbridge`).
EventBridge rules deliver events to a target (SQS, Lambda, API Destination) in the same or a central
account; audit-service (or a consumer in the central account) persists.
The app never "calls back" to audit-service in that path. “calls back” to audit-service in that path.

| Path        | Emitting service sends to | Where audit-service runs |
|------------|----------------------------|---------------------------|
| HTTP (now) | POST to audit-service     | Same or any account (URL configured) |
| EventBridge (future) | PutEvents to bus in app account | Central account, consuming from rule target |

---

## Configuration

- **Emitting services:** `quarkus.rest-client.AuditServiceClient.url`
  (e.g. `http://localhost:${AUDIT_SERVICE_PORT}`).
  Actor/correlation from request context or from auth result (login/register).
- **Audit-service:** Port, Postgres, `@AllowedServices`; optional EventBridge consumer config later.

---

## Implementation notes

- **Cross-cutting:** Annotate only methods (or types) that need audit.
- **Sync send:** Publisher currently sends synchronously to avoid async/context issues; async
  (e.g. fire-and-forget like notification-service) can be reintroduced once stable.
- **ActorId:** Resolver chain: request context first, then AuthResponse/RegistrationResponse for
  login/register (no JWT on those requests).
- **PII:** Mask or redact sensitive fields in argPaths/metadata where needed.
- **EventBridge:** Use PutEvents; detail-type/source for routing; idempotency by eventId where possible.

---

## References

- Cross-cutting: forge-kit `LogMethodEntry`, `LogMethodEntryInterceptor`; `libs/security` interceptors.
- Centralized alerting: [Build Centralized Alerting with CloudWatch, EventBridge, Lambda, and CDK](https://awsfundamentals.com/blog/build-centralized-alerting-across-your-organization-with-cloudwatch-eventbridge-lambda-and-cdk).
- Platform: [COMMERCIAL_PLATFORM_SERVICES.md](COMMERCIAL_PLATFORM_SERVICES.md). Notification pattern: ADR [0015](../architecture/decisions/0015-notification-service-fire-and-forget-pattern.md).
