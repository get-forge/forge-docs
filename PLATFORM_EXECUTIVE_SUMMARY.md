# Forge Platform: Executive Summary

**Audience:** Leadership, partners, and new stakeholders  
**Scope:** One-page, high-level view of what the platform is, how it runs, and what ships today  
**Last updated:** 2026-03-30

## What Forge Is

- **Zero-trust microservices platform** on **AWS** for scalable web products.
- **Java 25** and **Quarkus** for APIs and services; **Maven monorepo** with shared libraries (security, observability, health, shared policies).
- **Purpose:** ship **authentication, domain APIs, observability, and repeatable deployment** so new products build on a known foundation.

## Business and Technical Value

- **Security by default:** Stateless **JWT** with **AWS Cognito**,
  **service-to-service** auth, encrypted config (**SmallRye Config Crypto** +
  **AWS Systems Manager Parameter Store**), and **network controls** (WAF,
  security groups, private subnets) on public and management paths.
- **Operational confidence:** **OpenTelemetry** and **Jaeger** for tracing;
  **Micrometer**, **Prometheus**, and **Grafana** for metrics; **readiness**
  health checks suited to ALB and ECS.
- **Resilience:** **MicroProfile Fault Tolerance** (timeouts, retries, circuit breakers) on
  critical paths; **rate limiting** where enforced in the stack.
- **Engineering discipline:** Checkstyle, PMD, SpotBugs, OWASP dependency checks; **GitHub Actions**; **Renovate**; **ADRs**; conventional commits.

## What Runs in Production (Shape)

- **Compute:** **Amazon ECS on Fargate** behind an **Application Load Balancer**; container images in **ECR**.
- **Data:** **PostgreSQL (RDS)**, **DynamoDB**, **S3** by service need; **Cognito** for identity.
- **IaC:** **AWS CDK v2** (TypeScript) in `infra/` as a **stage**: **Network** (VPC), **Domain**
  (DNS, certificates, SES-related outputs for mail), **Datastore**, **Security** (WAF,
  Cognito integration, related controls), **Runtime** (ECS services). Stacks declare
  explicit dependencies.

## Services and Applications (Current)

- **API / BFF:** `applications/backend-actor` (routes include auth, actors, documents, admin).
- **Services:** `auth-service`, `actor-service`, `document-service`, `audit-service`, **`notification-service`**.
- **Web:** `ui/web-actor`.
- **Routing:** ALB path patterns send traffic to the correct service; **Cognito service accounts** back service-to-service calls where configured.

## Notifications (First Delivery)

**`notification-service`** is the first pass of **centralized notifications**:

- **Email** via **AWS SES**; **provider interface** ready for more channels later.
- **Templates** (Qute); **PostgreSQL** for notification records and processing.
- **Asynchronous, fire-and-forget** flow; **retries** and **unsubscribe** with opaque
  tokens (ADRs **0015-0019** under `docs/architecture/decisions/`).
- **SMS and push** are not in this first phase (see `services/notification-service/README.md`).

## Delivery Pipeline (High Level)

**GitHub Actions** uses **OIDC** to AWS (no long-lived CI keys):

- **Every push to `main`:** hygiene checks, **build and test**, static analysis, and coverage workflows as configured.
- **Application delivery:** when `applications/`, `services/`, `ui/`, or the centralized
  service list change, **package and push images** for **changed modules only**, then
  **deploy those images** to ECS on Fargate.
- **Full-stack infra (CDK):** **manual bootstrap** workflow seeds shared AWS resources
  (for example ECR and domain-related stacks); on success, **ECR image seed** runs, then
  **CDK synth, test, and deploy** applies the `infra/` app. This chain is for
  **infrastructure evolution**, not every application commit.

For exact job names and `on:` triggers, see `.github/workflows/`.

## Where to Go Deeper

- **Broader technical backlog and history:** `docs/architecture/ARCHITECTURAL_IMPROVEMENTS.md`
  (some items evolve as IaC and services land; cross-check against code).
- **Notification design:** `docs/pivot/NOTIFICATION_SERVICE_DESIGN.md` and `services/notification-service/README.md`.
- **Repository map and stack details:** root `README.md` and `infra/` documentation.

---

*This document is maintained to track the platform story for executives and onboarding;*

*Update it when major capabilities (new services, deployment model, or compliance posture) change.*
