# Documentation

The Forge Platform consists of the following discreet repositories:

| Repository                                                    | Visibility | Description                                                                                         |
|---------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------------|
| [forge-kit](https://github.com/get-forge/forge-kit)           | Public     | Infrastructure components for Quarkus services: rate limiting, metrics, health checks.              |
| [forge-core](https://github.com/get-forge/forge-core)         | Private    | A zero-trust, horizontally scalable microservices platform built with Quarkus, and deployed on AWS. |
| [forge-platform](https://github.com/get-forge/forge-platform) | Private    | A filtered mirror of `forge-core` that clients will fork, own and run with a licence.               |
| [forge-docs](https://github.com/get-forge/forge-docs)         | Public     | This public documentation repository.                                                               |

[forge-kit](https://github.com/get-forge/forge-kit) is open-sourced and intended as a
limited but useful showcase of operational best practices that anyone can re-use with
existing Quarkus services. It is also a working dependency of `forge-core`.

## How it works
- You purchase a Forge Platform licence file from the [Forge Platform website](https://forgeplatform.software/#pricing).
- Your organization is added as a Contributor, so you can fork the [forge-platform](https://github.com/get-forge/forge-platform) repository.
- You then own and develop that forked codebase.
- You provision the provided CI/CD workflows in your own GitHub account (works in free tier GitHub Actions).
- You deploy the platform to your own AWS accounts (development works in free tier AWS).
- You receive any future platform updates by syncing your fork to the upstream `forge-platform` repository.

## What you get

Out of the box, the Forge Platform provides you with the following:

- A development environment built predominantly on free tier LocalStack that emulates AWS in full and spins up in seconds.

[![Local services](assets/forge-services-local.png)](assets/forge-services-local.png)

- An entire GitHub Actions pipeline which includes release automation; ECS deployments (diffed services only); infrastructure
  deployments (CDK); static code analysis (OWASP, SpotBugs, etc); code coverage, unit/integration test reports, and more.

[![GitHub Actions workflows](assets/forge-github-workflows.png)](assets/forge-github-workflows.png)

- Full IaC support and repeatable automation for AWS environments, including thoughtful segregation of stateful vs stateless resources.

[![AWS CloudFormation stacks](assets/forge-sandbox-aws-cloudformation.png)](assets/forge-sandbox-aws-cloudformation.png)

- A clean, well-documented, and well-tested codebase that you can fork and modify.
- A stateless reference web application that you can deploy locally and to AWS and use immediately.

<!-- markdownlint-disable MD033 -->
<p align="center">
  <a href="assets/forge-web-home.png">
    <img src="assets/forge-web-home.png" alt="Forge reference web homepage" width="32%" />
  </a>
  <a href="assets/forge-web-login.png">
    <img src="assets/forge-web-login.png" alt="Forge reference web login" width="32%" />
  </a>
  <a href="assets/forge-web-dashboard.png">
    <img src="assets/forge-web-dashboard.png" alt="Forge reference web dashboard" width="32%" />
  </a>
</p>
<!-- markdownlint-enable MD033 -->

- The following foundational services provide the base for you to build domain services (e.g. search, quote, booking, etc.):
  - actor-service; canonical user profile and identity-linked domain data
  - audit-service; immutable event and action trail for compliance and observability
  - auth-service; JWT issuance, validation, and user/service authentication workflows
  - document-service; document metadata, storage orchestration, and retrieval APIs
  - notification-service; template-driven outbound messaging and delivery orchestration

- The following edge services that provide client-facing composition and delivery layers:
  - backend-actor; BFF orchestration tier
  - backend-web; disposable reference UI and consumable frontend

- Comprehensive Prometheus metrics and Grafana dashboards for observability.

<!-- markdownlint-disable MD033 -->
<p align="center">
  <a href="assets/forge-metrics-dashboard.png">
    <img src="assets/forge-metrics-dashboard.png" alt="Forge metrics dashboard" width="48%" style="vertical-align: top;" />
  </a>
  <a href="assets/forge-metrics-database.png">
    <img src="assets/forge-metrics-database.png" alt="Forge database metrics" width="48%" style="vertical-align: top;" />
  </a>
</p>
<!-- markdownlint-enable MD033 -->

For the complete list of platform features, see the [FEATURES.md](architecture/FEATURES.md) file.

## Deployment architecture overview

#### Workloads and services
![Architecture diagram (sandbox)](assets/forge-sandbox.svg)

#### VPC / network
![Architecture diagram (VPC overview)](assets/forge-sandbox-vpc.svg)

## In-depth guides

| Guide                                                                      | Description                                                                   |
|----------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [USER_AUTHENTICATION.md](architecture/guides/USER_AUTHENTICATION.md)       | JWT, Cognito form login, LinkedIn OAuth, filters, and OIDC **configuration**. |
| [SERVICE_AUTHENTICATION.md](architecture/guides/SERVICE_AUTHENTICATION.md) | Service accounts, `@AllowedServices`, client filters.                         |
| [CACHING.md](architecture/guides/CACHING.md)                               | Quarkus Cache names and where they apply.                                     |
| [METRICS.md](architecture/guides/METRICS.md)                               | Micrometer, `/q/metrics`, forge-kit metrics, local Grafana.                   |
| [HEALTH_CHECK.md](architecture/guides/HEALTH_CHECK.md)                     | Readiness checks, forge-health-aws, per-service wiring.                       |
| [AUDIT_SERVICE.md](architecture/guides/AUDIT_SERVICE.md)                   | Audit library and `audit-service` HTTP ingest.                                |
| [NOTIFICATION_SERVICE.md](architecture/guides/NOTIFICATION_SERVICE.md)     | Notification delivery, templates, SNS webhook spec.                           |

## Architecture Decision Records (ADRs)

This repository contains a public, sanitized subset of Architecture Decision Records (ADRs) from the Forge Platform.

ADRs document consequential engineering decisions—context, constraints, alternatives, and rationale—as a durable record
of *why* the system is shaped as it is.

### Scope and redaction

Not all ADRs are published. The records included here:

- Represent decisions relevant to Forge Platform consumers
- Exclude sensitive details (e.g. security mechanisms, internal topology, proprietary workflows)
- May be abstracted or generalized from their original form

### Interpretation notes

- ADRs are **historical artifacts**, and are not always reflective of the current implementation
- Decisions may evolve; superseding ADRs will be linked where applicable
- Trade-offs and rejected alternatives are preserved to aid understanding

### ADR index

- [0001-record-architecture-decisions.md](architecture/decisions/0001-record-architecture-decisions.md)
- [0002-adoption-of-quarkus-for-api-and-controller-tier.md](architecture/decisions/0002-adoption-of-quarkus-for-api-and-controller-tier.md)
- [0003-authentication-and-user-management-approach.md](architecture/decisions/0003-authentication-and-user-management-approach.md)
- [0004-use-aws-cognito-across-all-environments.md](architecture/decisions/0004-use-aws-cognito-across-all-environments.md)
- [0005-implement-service-auth-sts-assumerolewithwebidentity.md](architecture/decisions/0005-implement-service-auth-sts-assumerolewithwebidentity.md)
- [0006-internal-third-party-api-wrapper-standalone-service-vs-library.md](architecture/decisions/0006-internal-third-party-api-wrapper-standalone-service-vs-library.md)
- [0007-observability-strategy.md](architecture/decisions/0007-observability-strategy.md)
- [0008-decoupling-micro-services-transitioning-from-rest-to-sqs.md](architecture/decisions/0008-decoupling-micro-services-transitioning-from-rest-to-sqs.md)
- [0009-user-profile-storage-strategy.md](architecture/decisions/0009-user-profile-storage-strategy.md)
- [0010-rest-api-design-standards.md](architecture/decisions/0010-rest-api-design-standards.md)
- [0011-stateless-jwt-authentication.md](architecture/decisions/0011-stateless-jwt-authentication.md)
- [0012-clean-architecture-package-structure.md](architecture/decisions/0012-clean-architecture-package-structure.md)
- [0013-migration-from-mapstruct-to-manual-mappers.md](architecture/decisions/0013-migration-from-mapstruct-to-manual-mappers.md)
- [0014-application-caching-strategy.md](architecture/decisions/0014-application-caching-strategy.md)
- [0015-notification-service-fire-and-forget-pattern.md](architecture/decisions/0015-notification-service-fire-and-forget-pattern.md)
- [0016-notification-service-rate-limiting-strategy.md](architecture/decisions/0016-notification-service-rate-limiting-strategy.md)
- [0017-notification-service-template-implementation.md](architecture/decisions/0017-notification-service-template-implementation.md)
- [0018-notification-service-template-engine.md](architecture/decisions/0018-notification-service-template-engine.md)
- [0019-notification-service-unsubscribe-token-security.md](architecture/decisions/0019-notification-service-unsubscribe-token-security.md)
- [0020-single-vpc-per-environment.md](architecture/decisions/0020-single-vpc-per-environment.md)
- [0021-user-environment-aware-removal-policies-for-cdk-managed-resources.md](architecture/decisions/0021-user-environment-aware-removal-policies-for-cdk-managed-resources.md)
- [0022-public-alb-edge-and-origin-protection.md](architecture/decisions/0022-public-alb-edge-and-origin-protection.md)
- [0023-dnssec-route53-hosted-zones-baseline.md](architecture/decisions/0023-dnssec-route53-hosted-zones-baseline.md)
- [0024-internal-alb-tls-east-west-optional.md](architecture/decisions/0024-internal-alb-tls-east-west-optional.md)

## Onboarding and operations runbooks

- [README.md](https://github.com/get-forge/forge-platform/blob/main/README.md) — first screen on GitHub; fork and setup
  narrative
- [DEVELOPMENT.md](https://github.com/get-forge/forge-platform/blob/main/docs/DEVELOPMENT.md) — local dev, tooling,
  licence, Quarkus
- [OPERATIONS.md](https://github.com/get-forge/forge-platform/blob/main/docs/OPERATIONS.md) — GitHub OIDC, Actions, CDK,
  AWS/LocalStack
- [CHEATSHEET.md](https://github.com/get-forge/forge-platform/blob/main/docs/CHEATSHEET.md) — `task` index and
  copy-paste
- [infra/README.md](https://github.com/get-forge/forge-platform/blob/main/infra/README.md) — CDK layout and entry points
