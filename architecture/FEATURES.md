# Platform features (technical)

A capability-led view of what Forge offers engineers and operators—**what you get**, not how the wiring
is done. Deeper how-to material is in the [Guides](../README.md#guides) section of
the [documentation index](../README.md).

## Engineering base

- **Monorepo delivery** — One place for BFFs, domain services, shared libraries, UIs, shared configuration, and
  infrastructure as code, with a single Java/Quarkus toolchain and optional native image builds.
- **Modern Java** — Current Java and Quarkus for fast dev cycles, small containers, and cloud-friendly runtimes.
- **Shared platform libraries** — Common security, metrics, throttling, health, and licence/reactor support via
  [**get-forge/forge-kit**](https://github.com/get-forge/forge-kit) (
  see [Shared platform (forge-kit)](#shared-platform-forge-kit) below).

## Security and identity

- **Stateless JWTs end-to-end** — No server-side sessions; APIs consume bearer tokens, scale horizontally, and stay
  simple to reason about in load-balanced environments.
- **First-party sign-in (Cognito)** — Email/password and registration go through your **own** product UI; users are
  not bounced to a generic Cognito-hosted sign-in page for the primary flow.
- **Federated sign-in (LinkedIn)** — Social login for users who choose it, with a clear browser OAuth path separate
  from the email/password model.
- **Service identity** — Workloads can authenticate as first-class **service** principals, not only as end users,
  so background flows and service-to-service calls have a first-class model.
- **Service-level access control** — Sensitive APIs can be **closed to a named set of calling services**, reducing
  the risk that a valid **user** token alone is enough to hit internal or administrative surfaces.
- **Automatic request protection** — Inbound calls are evaluated by the security stack; token validation is designed
  for high throughput and uses caching where it materially reduces work on hot paths.
- **Throttling and abuse awareness** — Rate limiting is enforced early in the pipeline, with **metrics** that make it
  obvious who is over capacity (by user, service, IP, or unauthenticated traffic as configured).
- **Secret hygiene** — Sensitive configuration is encrypted at rest and can be bound to **AWS SSM** (or equivalent) in
  real environments.

## Observability and operations

- **Metrics out of the box** — **Prometheus**-compatible scraping on a standard path on every service, plus
  pre-built **Grafana** dashboards (HTTP, JVM, user/auth activity, throttles, infrastructure, database) for local and
  lab use.
- **Layered application metrics** — Beyond the usual HTTP and JVM story, the platform records **operation-level**
  success/failure and behaviour on domain services, **persistence and fault-tolerance** on critical data paths, and
  **throttle and rate-limit** health—not only raw request counts.
- **Dependency readiness** — Readiness checks understand **Postgres, DynamoDB, S3, Cognito, and SES**-style
  dependencies so load balancers and schedulers do not send traffic to a service that is structurally unable to
  work.
- **Consistent logging** — Shared logging conventions and correlation so incidents can be traced across tier and
  service boundaries.
- **Optional distributed tracing** — **OpenTelemetry** is integrated at the platform level and can be aimed at a
  collector (for example, next to a local **Jaeger** stack); shipping defaults are conservative and easy to turn on
  per environment.
- **Deployment-shaped health** — Public edge health checks line up with **ECS/Fargate**-style probes so **“green” in
  the load balancer** matches **“able to do real work”** for the workloads you run.

## Data, documents, and messaging

- **Relational data where it fits** — Strong **PostgreSQL** for profiles, audit trails, notification records, and
  other relational workloads.
- **High-throughput NoSQL** — **DynamoDB** for high-scale or key/value shapes (including notification templates and
  document-oriented stores where the product uses them).
- **Object storage** — **S3** for file and document storage with a clear separation from request/response paths.
- **Email at scale** — **Amazon SES** for notification delivery, with a path to richer delivery feedback (bounces,
  complaints) as you harden the pipeline.
- **Audit and compliance-friendly events** — Cross-service **low-touch** audit so important actions can be
  captured in a **central ingest** service without ad hoc logging in every call path.
- **Fire-and-forget notifications** — Asynchronous **notification** processing with **retries, templates, and
  unsubscribe** as first-class product concerns, not a one-off script.

## Domain services (what the platform includes)

- **Identity and session lifecycle** — Login, registration, token refresh, and the glue to user onboarding.
- **Actor (user) profile** — Profile read/write with caching to keep hot read paths lean.
- **Document intelligence** — Upload, store, and work with **documents and parsed** structures across SQL, NoSQL,
  and object storage.
- **Audit** — A dedicated **ingest and persistence** service for audit events, ready to evolve toward **event-bus**
  delivery later without rewrites at the edge.
- **Notification** — Central **email** pipeline and template model; room to grow into additional channels and richer
  operational behaviour.

## Applications and UIs

- **Persona-oriented BFFs** — “Backend for frontend” style apps that keep browser clients simple and keep domain
  calls on consistent, product-owned contracts (actor, admin, etc.).
- **Thin, static-friendly UIs** — Web modules that can be **served by Quarkus** and evolved without a separate
  node-based build in the hot path, where the product has standardized on that.

## Cloud and platform (AWS)

- **Infrastructure as code** — The whole **VPC, DNS, certificates, WAF, ALB, ECS on Fargate, ECR** story is expressed
  in a **CDK** application, so environments are repeatable and auditable.
- **Sensible public edge** — WAF, rate, and host rules to reduce casual abuse; **private** task networking so
  workloads are not needlessly exposed.
- **CI/CD** — **GitHub Actions** from hygiene through test, static analysis, coverage, artifact build, **container
  publish**, and **environment deploy**—a single, opinionated path from **merge** to **running in AWS**.

## Shared platform (forge-kit)

Open-source libraries, published as [**forge-kit**](https://github.com/get-forge/forge-kit), that speed up
observability, throttling, health for AWS, and small cross-cutting **API**s—so product repos stay about **domain**, not
plumbing. Representative modules:

| Module                                                                                                                 | What it brings to Forge                                                                                                             |
|------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| [**forge-metrics** / **forge-metrics-api**](https://github.com/get-forge/forge-kit/tree/main/forge-impl/forge-metrics) | Declarative **application metrics** (operations, DB and resilience surfaces, throttles) without bespoke meter wiring everywhere.    |
| [**forge-health-aws**](https://github.com/get-forge/forge-kit/tree/main/forge-impl/forge-health-aws)                   | **Readiness** for AWS-shaped dependencies (RDS-style Postgres, **DynamoDB, S3, Cognito**).                                          |
| [**forge-throttle**](https://github.com/get-forge/forge-kit/tree/main/forge-impl/forge-throttle)                       | **Rate limiting** primitives and metrics-friendly integration.                                                                      |
| [**forge-security**](https://github.com/get-forge/forge-kit/tree/main/forge-impl/forge-security)                       | Reusable **JWT** building blocks; Forge customises and orders claims extractors for Cognito.                                        |
| **forge-common** and others                                                                                            | Shared utilities (e.g. structured cross-cutting **logging/entry** patterns), licencing, reactor publishing—depending on the module. |

This list is a **tour** of the ecosystem, not an exhaustive module manifest; see the forge-kit repo for the current
graph.
