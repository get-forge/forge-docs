# Documentation

The [Forge Platform](https://forgeplatform.software/) consists of the following discreet repositories:

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

ADRs are historical *why* records (context, trade-offs, alternatives). The full public index, redaction scope, and links
to each decision can be found in **[architecture/ADRs.md](architecture/ADRs.md)**.

## Onboarding and operations runbooks

- [README.md](https://github.com/get-forge/forge-platform/blob/main/README.md) — first screen on GitHub; fork and setup narrative
- [DEVELOPMENT.md](DEVELOPMENT.md) — local dev, tooling, licence, Quarkus
- [OPERATIONS.md](OPERATIONS.md) — GitHub OIDC, Actions, CDK, AWS/LocalStack
- [CHEATSHEET.md](CHEATSHEET.md) — `task` index and copy-paste
- [infra/README.md](https://github.com/get-forge/forge-platform/blob/main/infra/README.md) — CDK layout and entry points
