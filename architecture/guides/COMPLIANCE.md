# Security and Compliance Control Mapping for Forked Deployments

Forge Platform itself cannot meaningfully represent a production deployment as "compliant" because Forge is not operated
as a centralized SaaS platform. Forge is always deployed, extended, configured, and operated inside AWS accounts owned
by the operator organization. Compliance outcomes therefore depend on the deployed environment, operational controls,
organizational processes, and legal agreements implemented by the operator.

## Purpose

This document maps Forge capabilities to common security and compliance expectations and clarifies what must be
implemented and evidenced by the organization operating a forked deployment.

- Forge provides a security-focused technical baseline designed to support common compliance objectives. Compliance
outcomes are achieved at the level of the deployed system and the operating organization, not at the framework or
source-repository level.
- Progress is a developer-assessed mapping of repository capabilities to common compliance expectations. It is not an
  audit determination, certification, attestation, or legal interpretation.

## Definitions

- **Operator**: The organization running a forked deployment of Forge in its own AWS accounts.
- **Forge Platform**: Reference services, libraries, and infrastructure as code (IaC) that operators can deploy
  and extend.

## Adoption stance (agnostic by design)

Forge intentionally ships a minimal-assumption, non-prescriptive baseline so teams can adopt it without redesigning
their AWS estate. The default developer path uses a sandbox profile (`forge-sandbox`) and avoids forcing a specific
enterprise landing-zone topology.

What this means in practice:

- Controls in this document are a reference and recommendation set, not a mandate for one AWS account model.
- Some operators run single-account or light setups; others run enterprise multi-account structures with centralized
  security tooling.
- Both paths can satisfy control objectives when required controls are implemented and evidenced in the operator
  environment.
- If you already use a landing-zone framework (for example, [Superwerker](https://superwerker.cloud/)), map Forge control
  objectives into that foundation instead of reshaping your organization around Forge.

## Responsibility split

| Party              | Role                                                                                                                                                                                                                                                                                                    |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Forge Platform** | Reference application, libraries, and CDK stacks operators can deploy. Security-relevant patterns (authn/authz, audit emission, infrastructure defaults) are implemented here when stated below.                                                                                                        |
| **Operator**       | Owns the AWS accounts, data classification, policies, workforce training, vendor agreements (for example BAAs for HIPAA), data processing agreements (DPAs) and lawful basis (GDPR), SOC 2 control operation and evidence, monitoring of the full AWS account, and gap closure outside this repository. |

Deploying Forge does not make an operator compliant. Compliance requires the deployed system, operating processes, and
legal agreements to work together.

## Architectural planes (audit vocabulary)

Auditors often partition systems into planes. The table below maps that vocabulary to how Forge is implemented and
operated.

| Plane                                | Meaning in Forge                                                                                          | Primary locations                                                                                                                                        |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Control plane**                    | Identity, tenant-scoped authorization patterns, audit emission, configuration, notification orchestration | `libs/security`, `services/auth-service`, `libs/audit`, `services/audit-service`, `services/notification-service`, `config/`                             |
| **Audit and governance plane**       | Immutable event capture, compliance logging, traceability of actions across actors and services           | `libs/audit`, `services/audit-service`, `docs/architecture/guides/AUDIT_SERVICE.md`                                                                      |
| **Data plane**                       | Domain data in RDS, DynamoDB, S3 as provisioned by CDK                                                    | `infra/src/lib/stack/forge-datastore-stack.ts`, service Flyway schemas                                                                                   |
| **Observability and security plane** | Logging, metrics, tracing hooks; edge and network controls                                                | `config/src/main/resources/logging.properties`, `otel.properties`, `infra` (WAF, VPC, ECS), `docs/architecture/decisions/0007-observability-strategy.md` |

## Interpretation model

The tables below intentionally separate three layers:

- What is implemented in this repository.
- What is typically expected in compliance frameworks.
- What the operator must implement, configure, and evidence in the deployed environment.

## Progress checklist (repository and typical deploy)

Progress is expressed using the symbols below for a standard AWS deployment.

| Symbol | Meaning                                                                                                       |
|:------:|---------------------------------------------------------------------------------------------------------------|
|   ✅    | Implemented in this repository for the standard deploy path.                                                  |
|   🟠   | Partial coverage: building blocks exist, but operator wiring, policy, or additional work is still required.   |
|   ❌    | Not implemented in this repository: gap, roadmap item, or operator-owned without first-class automation here. |

### Identity and access management

| Progress | Item                                                                 | Notes                                                                                                     | Framework hints                                                                                                                                                 |
|:--------:|----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
|    ✅     | Human authentication via Amazon Cognito User Pools (JWT)             | Actor pool and app client; password policy on service pool; see ADR-0004 and `infra` Cognito stacks       | Supports control objectives commonly associated with SOC 2 CC6.x and HIPAA §164.312(d), when operated alongside required organizational policies and procedures |
|    ✅     | Service-to-service JWT and `@AllowedServices`                        | `libs/security`, Cognito service pool, secrets for service accounts                                       | Supports least-privilege control objectives commonly associated with SOC 2 CC6.x                                                                                |
|    🟠    | AWS STS `AssumeRoleWithWebIdentity` for AWS API access from services | ADR-0005; operator must align IAM roles and pool trust per environment                                    | Supports control objectives commonly associated with SOC 2 CC6.x when combined with operator IAM governance                                                     |
|    ❌     | Customer SSO (SAML or OIDC IdP federation)                           | Current docs focus on Cognito-native flows; enterprise IdP federation is an operator integration topic    | Supports SOC 2 CC6.x control objectives when implemented                                                                                                        |
|    ✅     | GitHub Actions OIDC to AWS (`id-token: write`)                       | `.github/workflows/02-build-test.yml` and deploy workflows; `ForgeGithubRoleConstruct` pattern in `infra` | Supports supply-chain related control objectives commonly associated with SOC 2 CC8.x                                                                           |
|    ❌     | Long-lived IAM user keys for runtime                                 | Design favors roles and Cognito; operators should avoid long-lived credentials in their accounts          | Misconfiguration risk under SOC 2 CC6.x objectives                                                                                                              |

### Network and edge

| Progress | Item                                                        | Notes                                                 | Framework hints                                                                                                 |
|:--------:|-------------------------------------------------------------|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
|    ✅     | Single VPC per environment with public/private subnet split | ADR-0020; `ForgeNetworkConstruct`                     | Supports control objectives commonly associated with SOC 2 CC6.x                                                |
|    ✅     | ECS tasks in private subnets                                | `infra/README.md` deployment architecture             | Defense in depth for common network control expectations                                                        |
|    ✅     | Internet-facing ALB only where configured; WAF association  | Infrastructure constructs (WAF ARN export, ALB rules) | Supports control objectives commonly associated with SOC 2 CC6.x when WAF policies are configured and monitored |
|    ✅     | VPC interface endpoint for Secrets Manager                  | `ForgeNetworkConstruct`                               | Reduces internet exposure for secret retrieval                                                                  |
|    ❌     | Perimeter hardening (Shield Advanced, custom threat models) | Not codified as a default in this repository          | Risk-based operator decision                                                                                    |

### Data protection

| Progress | Item                                                        | Notes                                                                                                               | Framework hints                                                                                                        |
|:--------:|-------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
|    ✅     | RDS PostgreSQL with encryption at rest                      | `storageEncrypted: true` on instance; see `ForgePostgresConstruct`                                                  | Supports encryption-at-rest objectives commonly associated with SOC 2 CC6.x and HIPAA §164.312(a)(2)(iv) when in scope |
|    ✅     | RDS in VPC private subnets; SG allows DB port from VPC CIDR | `ForgePostgresConstruct`                                                                                            | Network isolation patterns                                                                                             |
|    ✅     | Automated RDS backups with stage-based retention            | `rdsAutomatedBackupRetentionFor(stage)`                                                                             | Availability objectives depend on operator RPO/RTO requirements                                                        |
|    ✅     | DynamoDB and S3 for platform data                           | `ForgeDatastoreStack`; S3 default encryption (managed keys) in `ForgeS3Construct`                                   | Operators classify data per table and bucket                                                                           |
|    🟠    | Customer-managed KMS keys (CMKs) everywhere                 | RDS uses an AWS-managed pattern in current CDK; operators can extend for CMK requirements                           | Many enterprise and regulated environments require CMK controls                                                        |
|    🟠    | Row-level tenant isolation in application layer             | Multi-tenant patterns depend on product usage; no single enforced row-level security (RLS) story is documented here | Requires data model review and operator enforcement of tenant boundaries                                               |

### Secrets and configuration

| Progress | Item                                                                                 | Notes                                                                                | Framework hints                                                                 |
|:--------:|--------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
|    ✅     | ECS injects secrets from Secrets Manager (DB, Cognito, OAuth, licence, SmallRye key) | `ForgeFargateServiceConstruct`                                                       | Supports credential handling objectives commonly associated with SOC 2 CC6.x    |
|    ✅     | Service account password rotation via Secrets Manager                                | Cognito service account constructs and rotation Lambdas                              | Supports credential lifecycle expectations                                      |
|    ✅     | No secrets committed to application source                                           | Secrets supplied via environment variables or Secrets Manager; GitHub secrets for CI | Supports SOC 2 CC6.x and supply chain objectives commonly associated with CC8.x |
|    ✅     | Centralized configuration service                                                    | `config/` module and `platform-config.yml` pattern                                   | Supports change management expectations                                         |

### Audit and logging

| Progress | Item                                                                                   | Notes                                                                                                    | Framework hints                                                                                                      |
|:--------:|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
|    ✅     | Annotation-driven audit events to `audit-service`                                      | `libs/audit`, Postgres `audit.audit_events`, `docs/architecture/guides/AUDIT_SERVICE.md`                 | Supports monitoring and audit objectives commonly associated with SOC 2 CC7.x and HIPAA audit controls when in scope |
|    🟠    | EventBridge dispatch path                                                              | Placeholder or future wiring; HTTP to `audit-service` is the current path                                | Can support centralized routing patterns when completed                                                              |
|    ❌     | Immutable central audit store (for example S3 Object Lock, dedicated security account) | Architecture discusses future EventBridge pipeline; not fully automated here                             | Operator-owned control for many audit expectations                                                                   |
|    ❌     | AWS CloudTrail organization trail                                                      | Account-level logging is not defined in CDK in this repository                                           | Common expectation for AWS API audit evidence                                                                        |
|    🟠    | PII-safe logging discipline                                                            | Guides mention masking; `logging.properties` sets categories; operators need log review and DLP controls | Supports GDPR Articles 5 and 32 discussions when operated appropriately                                              |

### Observability

| Progress | Item                                 | Notes                                                                                                                  | Framework hints                                                             |
|:--------:|--------------------------------------|------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
|    ✅     | OpenTelemetry in Quarkus             | `otel.properties`, ADR-0007                                                                                            | Supports traceability objectives                                            |
|    🟠    | JSON logging strategy for CloudWatch | ADR-0007; local defaults exist; Fluent Bit or agent wiring is deploy-time                                              | Supports operational monitoring objectives when operator wiring is complete |
|    🟠    | Metrics and dashboards               | Metrics libraries and Grafana/Prometheus assets exist for development use; full AWS monitoring story is operator-owned | Supports monitoring objectives when operator alerting and runbooks exist    |

### Change management and secure engineering

| Progress | Item                                           | Notes                                                                                                                             | Framework hints                                                            |
|:--------:|------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
|    ✅     | CI build and test on push or PR                | `.github/workflows/02-build-test.yml`                                                                                             | Supports change management objectives commonly associated with SOC 2 CC8.x |
|    ✅     | Static analysis workflow                       | `.github/workflows/50-static-analysis.yml`                                                                                        | Supports SOC 2 CC8.x objectives                                            |
|    ✅     | Secret scanning action                         | `.github/actions/secret-scan`                                                                                                     | Supports SOC 2 CC8.x objectives                                            |
|    ✅     | CDK Nag for IaC checks                         | Suppressions exist where documented; operators should review                                                                      | Supports IaC governance patterns                                           |
|    🟠    | Dependency update automation and code scanning | `renovate.json` exists for dependency updates; Dependabot and CodeQL workflows are not configured in `.github` at time of writing | Operator-owned supply chain posture decisions                              |
|    ✅     | Signed commits on doc mirror                   | Mirror workflow uses GPG import for rewritten history; separate from application runtime                                          | Governance signal, not runtime control                                     |

### Data subject rights and retention (GDPR-forward)

| Progress | Item                                             | Notes                                                                                                                            | Framework hints                                                   |
|:--------:|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
|    🟠    | User deletion hooks in auth layer                | Cognito `deleteUser` used from auth flows; downstream data (RDS, DynamoDB, S3) requires operator processes or product completion | Supports GDPR Article 17 procedures when operator workflows exist |
|    ❌     | Export of personal data per user                 | No single documented export API covering all stores                                                                              | Supports GDPR Articles 15 and 20 procedures when implemented      |
|    🟠    | Configurable legal retention policies per tenant | CDK removal policies and RDS backup retention are stage-aware; not a full legal hold capability                                  | Requires operator retention policy and legal review               |
|    ✅     | Notification unsubscribe and token safety        | ADR-0019; opaque tokens; no PII in URLs                                                                                          | Supports consent and token safety expectations                    |

### Messaging and notifications

| Progress | Item                                                        | Notes                                                                 | Framework hints                                |
|:--------:|-------------------------------------------------------------|-----------------------------------------------------------------------|------------------------------------------------|
|    ✅     | Notification service with template engine and rate limiting | ADRs 0015-0018, `services/notification-service`                       | Supports abuse prevention objectives           |
|    ❌     | SQS as primary internal transport                           | ADR-0008 keeps synchronous REST for now; async-ready boundaries exist | Operator reliability requirements drive choice |

### Multi-account and org-wide security services

| Progress | Item                                                            | Notes                                                                                                                                       | Framework hints                          |
|:--------:|-----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------|
|    ❌     | AWS Organizations layout (security OU, log archive, separation) | Not generated by this CDK app; optional recommendation for operators using enterprise landing zones (for example Superwerker or equivalent) | Common expectation in larger deployments |
|    ❌     | AWS Config, Security Hub, GuardDuty                             | Not provisioned here                                                                                                                        | Operator-owned posture controls          |

## SOC 2 (Trust Services Criteria) quick map

Typical mappings auditors discuss. Criteria labels vary by report.

| Theme                                 | How Forge helps                                             | What the operator still proves                                                |
|---------------------------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------|
| **CC6 (logical and physical access)** | Cognito, JWT, service restrictions, private networking, WAF | Access reviews, administrator MFA, break-glass, staff IdP, workforce training |
| **CC7 (system operations)**           | Health checks, observability hooks, audit event library     | Incident response, alerting on AWS and application logs, backup restore tests |
| **CC8 (change management)**           | GitHub Actions, IaC, tests                                  | Production approvals, segregation of duties, release records, change reviews  |

## GDPR-oriented notes

- **Controller vs processor**: Operators are typically controllers for their users' data. Forge is a tool they operate.
  Legal roles depend on contracts and facts, not this file.
- **Technical measures**: Encryption in transit (TLS termination at ALB), encryption at rest on RDS, secrets handling,
  and
  audit hooks support GDPR Article 32 security-of-processing discussions when operators complete logging, monitoring,
  and
  incident practices.
- **Data subject rights**: Operators implement procedures (and possibly product features) for access, rectification,
  erasure, and portability across all stores, including Cognito, RDS, DynamoDB, and S3.

## HIPAA-oriented notes (high level)

HIPAA compliance depends on a BAA, scoped systems, and operational safeguards. Forge can support technical safeguards
commonly expected in HIPAA-aligned deployments (access control, audit records, encryption, transmission security) when
deployed and operated appropriately, but:

- No BAA is offered by this document.
- PHI must be classified; operators close gaps (for example immutable centralized audit storage, complete monitoring,
  vendor agreements with subprocessors such as AWS).

## Related documentation

- `infra/README.md` - AWS layout, stacks, security posture overview.
- `docs/architecture/guides/AUDIT_SERVICE.md` - Audit pipeline and PII guidance for emitters.
- `docs/architecture/guides/USER_AUTHENTICATION.md`, `docs/architecture/guides/SERVICE_AUTHENTICATION.md` - Auth flows.
- ADRs under `docs/architecture/decisions/` - Especially 0004 (Cognito), 0005 (STS), 0007 (observability), 0008
  (REST vs SQS), 0011 (JWT), 0019 (notification tokens), 0020 (VPC), 0021 (removal policies), 0024 (internal TLS and
  sensitivity).

## Maintaining this document

When you add material controls (for example CloudTrail stacks, organization-level KMS, data export APIs), update the
tables and progress symbols so operators retain an accurate picture. Prefer linking to ADRs and guides instead of
duplicating implementation detail.
