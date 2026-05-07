<!-- markdownlint-disable-file MD033 -->
<!-- MD033 off: inline HTML is used for spacing and image gallery layout where pure Markdown is insufficient. -->

# Documentation

## Forge Platform

Forge Platform provides a pre-engineered operational foundation for secure, scalable distributed systems on AWS,
dramatically reducing the time, cost, and risk of reaching production-grade security, scalability, and operational
maturity.

It encodes years of platform engineering decisions into a deployable operating model built on AWS with Quarkus.

Forge is not a framework. It is a pre-engineered operating model for building production systems.

### What you get at a glance

- Security model: zero-trust and identity-first request verification (JSON Web Token, OpenID Connect).
- Runtime model: stateless, horizontally scalable services deployed as containers on ECS Fargate.
- Observability model: metrics, dashboards, health endpoints, tracing support.
- Delivery and infrastructure model: repeatable pipelines and environments (IaC, CI/CD, complete GitHub Actions workflow
  pipeline).
- Developer experience: a complete local development environment that emulates AWS via LocalStack.

<br />

---

## Who Forge is for

Forge is designed for engineering teams building production systems where security, scalability, and operational
maturity must be correct from day one.

Typical adopters include:

- Teams building backend platforms or SaaS (Software as a Service) products on AWS that need security and operational
  maturity from day one.
- Organizations standardizing on Java, Maven, and Quarkus for service development.
- Founders shaping engineering organisations around proven platform patterns, without needing to build a large internal
  platform engineering function.
- Product teams accelerating time-to-market while reducing architectural uncertainty and operational risk.

Forge is a strong fit when the cost of getting platform decisions wrong is high: security posture, deployment model,
observability model, and identity boundaries need to be coherent from the outset.

<br />

---

## How Forge works

- Purchase a Forge Platform licence file from the [Forge Platform website](https://forgeplatform.software/#pricing).
- Your organization is added as a Contributor, so you can fork the `forge-platform` repository.
- You own and develop that forked codebase.
- Provision the provided CI/CD workflows in your GitHub account.
- Deploy into your AWS accounts. Development environments can run within AWS free tier limits.
- Receive future platform updates by syncing your fork to the upstream `forge-platform` repository.

You retain full ownership and control of infrastructure, services, deployments, and data.

<br />

---

## Start here

Most readers want the operating model first. These guides provide the fastest path to understanding how the Forge
Platform is built, how it runs, and how to extend it safely.

| Guide                                                                      | Why read it                                                                                                                      |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| [DEVELOPMENT.md](DEVELOPMENT.md)                                           | Local development, tooling, Quarkus workflow.                                                                                    |
| [OPERATIONS.md](OPERATIONS.md)                                             | Deployments, GitHub OIDC (OpenID Connect), AWS, LocalStack, CDK.                                                                 |
| [SECURITY.md](architecture/guides/SECURITY.md)                             | Platform security posture, least privilege, documented trade-offs.                                                               |
| [COMPLIANCE.md](architecture/guides/COMPLIANCE.md)                         | Security and compliance control mapping for forked deployments, including operator responsibilities.                             |
| [USER_AUTHENTICATION.md](architecture/guides/USER_AUTHENTICATION.md)       | Identity flows, JWT issuance/validation, Cognito login, OIDC configuration.                                                      |
| [SERVICE_AUTHENTICATION.md](architecture/guides/SERVICE_AUTHENTICATION.md) | Service accounts, `@AllowedServices`, service-to-service authorization.                                                          |
| [PERFORMANCE.md](architecture/guides/PERFORMANCE.md)                       | Performance test plan, outputs, phase summaries; conclusion showing repeatable throughput and predictable tail latency at scale. |

If you are evaluating Forge, start with Security and Operations. Those documents show the platform's decisions in the
open, including constraints and trade-offs.

<br />

---

## Security and compliance (overview)

Short summaries only. Full implementation detail, trade-offs, and shared-responsibility boundaries are documented in the
linked guides.

### Platform security architecture (summary)

Forge provides a production-oriented security baseline for running containerized distributed systems on AWS. Deployments
run entirely inside AWS accounts owned and controlled by the operator; Forge is not a managed SaaS control plane
([SECURITY.md](architecture/guides/SECURITY.md)).

The platform is designed around identity-first security and least-privilege infrastructure patterns. Baseline capabilities
include:

- JWT-based authentication and authorization for both users and services
- Application-layer authorization enforcement rather than trust-by-network-location
- Least-privilege IAM policies defined in infrastructure code
- ECS Fargate workloads isolated in private subnets
- Public ingress restricted to AWS ALBs protected by WAF controls
- TLS termination at the public edge
- Secrets managed through AWS Secrets Manager and Systems Manager Parameter Store
- GitHub Actions OIDC deployment flows without long-lived AWS deployment keys
- Repeatable AWS infrastructure provisioning through CDK
- Structured logging, metrics, and OpenTelemetry-based observability foundations

Forge intentionally documents architectural boundaries and progressive hardening paths. Operators can extend the baseline
with stronger transport security, centralized audit controls, organization-wide governance tooling, and environment-specific
compliance controls without redesigning the underlying platform architecture.

See [SECURITY.md](architecture/guides/SECURITY.md) for detailed coverage of trust boundaries, IAM posture, network
architecture, workload isolation, logging strategy, and shared responsibility.

### Security and compliance alignment (summary)

Forge is designed to support organizations building security-conscious and compliance-aware systems on AWS.
[COMPLIANCE.md](architecture/guides/COMPLIANCE.md) maps repository capabilities to control objectives commonly associated
with frameworks and programs such as SOC 2, GDPR, and HIPAA-aligned environments.

The platform provides a strong technical baseline including identity and access controls, infrastructure isolation,
secrets management, audit-event foundations, observability hooks, and repeatable infrastructure deployment patterns.

Forge itself does not claim compliance certification or attestation for client deployments. Each deployment is forked,
extended, configured, and operated independently inside operator-owned AWS environments. Compliance outcomes therefore depend
on the deployed system, operational processes, monitoring controls, legal agreements, and governance practices implemented
by the operator organization.

Progress states in the compliance guide reflect the current repository implementation posture and are intended as transparent
control mappings rather than audit findings or legal interpretations.

<br />

---

## Repository structure

The [Forge Platform](https://forgeplatform.software/) consists of the following discrete repositories:

| Repository                                            | Visibility | Description                                                                                                              |
|-------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------|
| [forge-kit](https://github.com/get-forge/forge-kit)   | Public     | Reusable operational components for Quarkus services.                                                                    |
| `forge-core`                                          | Private    | Internal upstream platform source.                                                                                       |
| `forge-platform`                                      | Private    | Client-forkable distributable platform, filtered mirror of `forge-core`.                                                 |
| [forge-docs](https://github.com/get-forge/forge-docs) | Public     | Public documentation repository, published at [get-forge.github.io/forge-docs](https://get-forge.github.io/forge-docs/). |

`forge-kit` is open source and can be adopted independently in existing Quarkus services. It is also a working
dependency of `forge-core`.

<br />

---

## What you get

Out of the box, the Forge Platform provides you with the following:

- A development environment built predominantly on free tier LocalStack that emulates AWS and spins up in seconds.

[![Local services](assets/forge-services-local.png)](assets/forge-services-local.png)

- An entire GitHub Actions pipeline which includes release automation; ECS deployments (diffed services only);
  infrastructure deployments (CDK); static code analysis (OWASP, SpotBugs); code coverage, unit/integration test
  reports, and more.

[![GitHub Actions workflows](assets/forge-github-workflows.png)](assets/forge-github-workflows.png)

- Full IaC support and repeatable automation for AWS environments, including thoughtful segregation of stateful vs
  stateless resources.

[![AWS CloudFormation stacks](assets/forge-sandbox-aws-cloudformation.png)](assets/forge-sandbox-aws-cloudformation.png)

- A clean, well-documented, and well-tested codebase that you can fork and modify.
  <br /><br />
- A stateless reference web application that you can deploy locally and to AWS and use immediately.

<p align="center">
  <a href="assets/forge-web-home.png">
    <img src="assets/forge-web-home.png" alt="Forge reference web homepage" width="33%" />
  </a>
  <a href="assets/forge-web-login.png">
    <img src="assets/forge-web-login.png" alt="Forge reference web login" width="33%" />
  </a>
  <a href="assets/forge-web-dashboard.png">
    <img src="assets/forge-web-dashboard.png" alt="Forge reference web dashboard" width="33%" />
  </a>
</p>

- The following foundational services provide the base for you to build domain services on top of:
  - actor-service; canonical user profile and identity-linked domain data
  - audit-service; immutable event and action trail for compliance and observability
  - auth-service; JWT issuance, validation, and user/service authentication workflows
  - document-service; document metadata, storage orchestration, and retrieval APIs
  - notification-service; template-driven outbound messaging and delivery orchestration
      <br /><br />
- The following edge services that provide client-facing composition and delivery layers:
  - actor-bff; BFF (Backend for Frontend) orchestration tier
  - backend-web; disposable reference UI and consumable frontend
      <br /><br />
- Comprehensive Prometheus (metrics) and Grafana (dashboards) for observability.

<p align="center">
  <a href="assets/forge-metrics-dashboard.png">
    <img src="assets/forge-metrics-dashboard.png" alt="Forge metrics dashboard" />
  </a>
  <br />
  <br />
  <a href="assets/forge-metrics-database.png">
    <img src="assets/forge-metrics-database.png" alt="Forge database metrics" />
  </a>
</p>

For the complete list of platform features, see the [FEATURES.md](architecture/FEATURES.md) file.

<br />

---

## Build vs. Buy

Forge exists to remove a class of problems that most teams eventually end up solving themselves.
You can build this platform internally. Many teams do. But in practice, that path comes with trade-offs:

### Time

- Building a production-ready foundation like this typically takes multiple years.
- Progress is incremental and often delayed by competing business priorities.

### Focus

Your team splits attention between:

- domain features (what your business actually sells)
- platform engineering (infrastructure, security, reliability, operations)

This dilution slows both tracks.

### Cost

The true cost includes more than engineering time:

- iteration cycles
- operational mistakes
- rework as standards evolve

### Opportunity cost

Every month spent building foundations is a month not spent:

- shipping differentiating features
- validating your market
- generating revenue

### What Forge changes

Forge compresses that entire journey into something you can adopt immediately:

- A production-ready foundation from day one
- A clear operational model aligned with modern cloud practices
- A Quarkus-first golden path with flexibility where you need it
- A platform that lets your team stay focused on domain and business value

Instead of building the runway, you start further down it.

### When it makes sense

Forge is a strong fit, if:

- You want to move quickly without building infrastructure from scratch
- Your team is domain-focused, not platform-heavy
- You value security, consistency, operability, and scale from the outset

If your goal is to invest heavily in building a bespoke internal platform, Forge may be less relevant.

<br />

---

## How Forge runs in production

Forge is designed to run as a container-native platform on AWS, using a small number of well-understood building blocks.

The operating model prioritizes:

- security best-practices
- predictable operations
- clear system reasoning
- alignment with modern service deployment practices

At a high level, Forge separates edge, services, and infrastructure concerns so each layer can scale and evolve
independently.

### High-level architecture

This view shows how traffic flows through the system:

- requests enter through the edge layer
- requests are routed to stateless application services
- services rely on managed infrastructure such as datastores and messaging

![Architecture diagram (sandbox)](assets/forge-sandbox.svg)

Key characteristics:

- Stateless services support horizontal scaling by default
- Clear boundaries simplify ownership and evolution
- Managed AWS services reduce operational overhead

### Service and runtime model

Each service in Forge follows a consistent runtime model:

- packaged as a container
- deployed via ECS Fargate without host management
- exposes standardized health, metrics, and operational endpoints

![Architecture diagram (VPC (Virtual Private Cloud) overview)](assets/forge-sandbox-vpc.svg)

The network model follows AWS VPC security best practices:

- public subnets host internet-facing edge components
- private subnets host application services and internal components
- security groups restrict east-west and north-south traffic to explicit service paths and ports
- public entry points terminate TLS and forward only required traffic inward

This runtime and network consistency enables:

- predictable, targeted deployments
- simpler debugging and operations
- reuse across multiple domains and teams

### What this means in practice

- You do not need to design your deployment model from scratch
- You inherit a setup aligned with enterprise production best practices
- Your team can focus on building services instead of building platform foundations

Forge gives you a starting point that is usable immediately, secure, and built to scale.

<br />

---

## In-depth architecture guides

| Guide                                                                  | Description                                                         |
|------------------------------------------------------------------------|---------------------------------------------------------------------|
| [CACHING.md](architecture/guides/CACHING.md)                           | Quarkus cache names and where they apply.                           |
| [METRICS.md](architecture/guides/METRICS.md)                           | Micrometer metrics, `/q/metrics`, forge-kit metrics, local Grafana. |
| [HEALTH_CHECK.md](architecture/guides/HEALTH_CHECK.md)                 | Readiness checks, forge-health-aws, per-service wiring.             |
| [AUDIT_SERVICE.md](architecture/guides/AUDIT_SERVICE.md)               | Audit library and `audit-service` HTTP ingest.                      |
| [NOTIFICATION_SERVICE.md](architecture/guides/NOTIFICATION_SERVICE.md) | Notification delivery, templates, SNS webhook spec.                 |

<br />

---

## Architecture Decision Records (ADRs)

ADRs (Architecture Decision Records) are historical "why" records: context, trade-offs, and alternatives. The full
public index, redaction scope, and links to each decision are in **[architecture/ADRs.md](architecture/ADRs.md)**.

<br />

---

## Operational documentation (post-fork / deployment reference)

- [DEVELOPMENT.md](DEVELOPMENT.md) - local dev, tooling, licence, Quarkus
- [OPERATIONS.md](OPERATIONS.md) - GitHub OIDC, Actions, CDK, AWS/LocalStack
- [CHEATSHEET.md](CHEATSHEET.md) - `task` index and copy-paste
