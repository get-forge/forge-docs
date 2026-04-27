<!-- markdownlint-disable-file MD013 -->
<!-- MD013 off: policy prose, long URLs, and table rows exceed the repository line length limit without harm to readability. -->

# Platform security posture

Forge is a production-oriented platform baseline for teams running containerized services on AWS. This guide explains the security controls that are implemented today, the risks that are intentionally accepted, and the controls that remain the responsibility of each client team.

This document is public and meant for architecture review, procurement review, and security review. User login and token lifecycle details live in [USER_AUTHENTICATION.md](USER_AUTHENTICATION.md). Service identity and service-level authorization details live in [SERVICE_AUTHENTICATION.md](SERVICE_AUTHENTICATION.md).

## Scope and operating model

Forge provisions and operates infrastructure inside AWS accounts owned by the client organization. The platform ships opinionated defaults for network topology, identity, workload runtime, and CI access patterns.

Forge is not a managed SaaS control plane. Client teams retain operational ownership of the AWS account, service code, data classification, key management decisions, and any controls required by their regulatory framework.

Forge follows a single-tenant deployment model. Each client environment is isolated in the client-owned AWS account boundary.

## Threat model baseline

Forge baseline assumptions:

1. Public endpoints are internet-exposed and should be treated as reachable by commodity attack traffic, including scanning and request flooding.
2. Internal network location is not sufficient proof of trust for protected operations.
3. Compromise of one service should not automatically authorize access to another service without explicit identity and authorization checks.

Forge baseline non-assumptions:

1. Full service-mesh zero-trust controls such as mTLS and workload identity federation are not mandatory in the baseline.
2. Deep detection and response controls such as SIEM correlation and SOC operations are organization-level concerns outside platform defaults.

## Security principles

Forge applies these principles by default:

1. Verify identity for user and service requests through JWT validation and explicit authorization checks.
2. Enforce least privilege in IAM policy design through narrow action sets and resource scoping.
3. Keep stateful infrastructure private by default and expose only explicit edge entry points.
4. Store sensitive configuration in managed secret stores instead of source control.
5. Apply a progressive hardening model where identity controls are always required and transport hardening is added per environment risk profile.

Progressive hardening means teams can layer stronger transport, key management, and policy controls without redesigning application-level identity and authorization logic.

No repository-authored baseline IAM policy or construct intentionally grants `Action: "*"`, and wildcard resource usage is explicitly documented and constrained.

## Network and edge security posture

## Trust boundaries

Forge uses explicit trust boundaries:

1. Internet to public ALB: untrusted traffic enters edge controls.
2. Public ALB to services: authenticated application boundary.
3. Service to service: identity-based trust through token validation, not network location.
4. AWS account boundary: primary tenant isolation boundary between client environments.

### VPC segmentation

Forge deploys a dedicated VPC per environment with:

- public subnets for internet-facing edge components
- private subnets for application workloads and data-plane dependencies
- `restrictDefaultSecurityGroup: true` to avoid permissive default security group behavior

Traffic between network zones is controlled through explicit security group rules. Reject-only VPC flow logs are enabled and retained for one year to support operational and forensic review.

### Security group posture

Current ingress posture is explicit and narrow:

- Public ALB security group allows inbound TCP `443` and TCP `80` from the internet.
- ECS service security group allows inbound application traffic only from ALB security groups.
- Internal ALB security group accepts traffic from ECS service security group only.

The current internal ALB path uses HTTP (`80`) for service-to-service calls. Identity and authorization remain enforced at the application layer through JWT validation and service authorization checks. HTTPS on the internal path is an available hardening step and is documented as part of the edge and origin roadmap in [ADR-0022](../decisions/0022-public-alb-edge-and-origin-protection.md).

Baseline implication: confidentiality of internal service traffic is not guaranteed by default transport settings and should be hardened for environments with stricter compliance or threat requirements.

Baseline traffic expectation: internal HTTP carries authenticated application payloads and is not designed as a transport channel for long-lived plaintext credentials. Client teams remain responsible for payload classification and required transport hardening for their risk profile.

### WAF and edge controls

Regional AWS WAF is attached to the public ALB with:

- host allowlist rule for the expected application hostname
- request-rate limiting rule
- geo-blocking rule

Host allowlist validation is intentionally treated as hygiene and traffic-shaping control, not cryptographic origin proof. A determined client can still present an allowed `Host` header. The target future posture is CloudFront in front of ALB with stronger origin verification controls, as recorded in [ADR-0022](../decisions/0022-public-alb-edge-and-origin-protection.md).

WAF provides best-effort edge filtering, not strong origin authentication.

## Identity and access management posture

### User and service identity

Forge separates human and workload identity:

- user authentication flows issue JWTs through Cognito-backed auth services
- service-to-service calls use dedicated service accounts and service JWTs
- receiving services can enforce caller allowlists through `@AllowedServices`

The platform does not rely on a trusted internal network assumption for service authorization. Services validate token identity and claims on each protected request.

### Least-privilege IAM implementation

IAM policies in infrastructure code are scoped to specific actions and resource sets wherever AWS APIs support resource-level constraints. Examples include:

- Cognito user-pool actions scoped to actor and service pool ARNs
- SSM reads scoped to specific Cognito parameter ARNs
- DynamoDB access scoped to known table and index ARNs
- S3 access split between bucket-level and object-level permissions
- Secrets Manager access scoped to required secret prefixes

### Wildcard usage policy and exceptions

Forge avoids broad wildcard permissions for administrative access. Current wildcard usage is limited to AWS API patterns that require wildcard resources or predictable suffix matching:

- `ecr:GetAuthorizationToken` uses `Resource: "*"` because AWS requires account-level token retrieval.
- SES send and account health actions use `Resource: "*"` because SES actions in use are account-scoped.
- Some Secrets Manager and index ARNs include wildcard suffixes for deterministic name patterns generated by AWS or deployment naming conventions.

No `Action: "*"` or administrator-style privilege grants are part of the baseline platform constructs.

## Secrets and credential handling

Forge stores application runtime-sensitive values in AWS Secrets Manager and AWS Systems Manager Parameter Store. ECS task and execution roles receive read access only for the secret paths and parameters required for runtime behavior.

GitHub repository and environment secrets are used for CI and CD workflow execution, bootstrap operations, and external integration credentials. GitHub secrets are never consumed directly by ECS runtime workloads and are not the system of record for application runtime secret retrieval.

Credential categories include:

- service account credentials for service authentication
- Cognito pool and client identifiers plus required client secrets
- encryption material and OAuth credentials needed by optional flows

Secret rotation is partially automated. The platform currently records explicit cdk-nag suppressions for selected secrets that do not have automatic rotation attached. Teams with stricter compliance requirements should enable managed rotation policies and key lifecycle controls per environment.

## Data protection controls

Encryption at rest posture in the baseline:

- S3 buckets are provisioned with server-side encryption (`S3_MANAGED`) and SSL enforcement.
- DynamoDB tables use DynamoDB-managed encryption at rest by default.
- Secrets Manager data is encrypted at rest through AWS KMS-backed service behavior.

Client hardening option: customer-managed KMS keys are supported as a configuration override per environment but are not required for baseline operation.

Encryption in transit posture in the baseline:

- public ingress terminates TLS at the public ALB
- internal service-to-service path currently uses HTTP and can be hardened to HTTPS in a later stage

## Runtime workload isolation and container privilege posture

Current baseline runtime posture:

- services run on ECS Fargate tasks, not on shared EC2 container hosts
- service tasks run in private subnets with no public IP assignment
- the shared Quarkus runtime image declares a non-root container user (`USER 1000`)

Current repository posture on privileged runtime flags:

- no repository-authored task definition sets privileged container mode
- no repository-authored task definition enables host networking for application services

Security interpretation:

- Forge baseline reduces host-level attack surface by combining Fargate isolation with non-root container execution
- Forge does not rely on privileged container capabilities for normal service operation

Future hardening options for stricter environments:

- read-only root filesystem where service runtime behavior allows it
- explicit container capability minimization and policy checks
- CI policy gates that fail builds when privileged runtime flags are introduced

## CI/CD and GitHub security posture

Forge uses GitHub Actions OpenID Connect (OIDC) for AWS role assumption. The trust policy constrains issuer, audience, and subject pattern so only approved workflow identities can assume the deployment role.

GitHub workflow permissions are scoped for deployment tasks:

- read-only managed policies for S3 and DynamoDB baseline access
- scoped ECR repository push and pull actions
- scoped ECS deployment actions for target cluster and services
- explicit bootstrap-role assumption permissions for CDK deploy flows

The platform does not require long-lived AWS access keys in GitHub for routine deployments once OIDC bootstrap is complete.

The baseline reduces credential supply chain risk by replacing long-lived CI cloud credentials with constrained, auditable OIDC role assumption.

## Logging and auditability posture

Platform logging controls currently implemented:

- VPC flow logs for rejected traffic, retained for one year
- ECS task and application logs through CloudWatch log groups
- WAF metrics and sampled requests through AWS WAF visibility configuration

Organization-level controls expected outside baseline platform constructs:

- CloudTrail strategy, retention, and governance settings
- ALB access logging strategy and storage lifecycle controls
- SIEM integration, alerting, and incident response workflow

Platform logging supports operational observability. Audit-grade retention, aggregation, and cross-system correlation are delegated to client environment configuration.

## Deliberate boundaries and non-goals

Forge documents security trade-offs so clients can evaluate fit by risk profile.

Current deliberate boundaries include:

1. Internal service traffic encryption is not forced in the baseline. The baseline favors broad compatibility and lower operational friction, while preserving a clear migration path to stronger in-transit controls.
2. Public ALB remains internet-routable by design in the interim posture. WAF host checks and security groups reduce accidental exposure but do not provide full origin authentication.
3. Security hardening features that depend on client-specific policy or compliance context remain configurable rather than mandatory defaults.

These boundaries are intentional design choices, not accidental gaps, and are documented through architecture decision records in [architecture/ADRs.md](../ADRs.md). The platform supports incremental adoption of stronger controls based on client maturity and threat model.

### Explicit non-goals

Forge baseline does not:

- mandate mTLS or service mesh adoption
- provide managed SOC, SIEM, or incident response operations
- assert compliance certification coverage for every client workload
- replace application-level authorization design in domain services

## Shared responsibility and client actions

Forge provides a strong baseline. Client teams should still implement:

- environment-specific data classification and retention policy
- production TLS and certificate lifecycle governance for all endpoints
- organization-level logging, SIEM integration, and alert response runbooks
- key management and rotation controls aligned with compliance requirements
- workload-specific authorization rules beyond platform defaults

## Security maturity roadmap alignment

Security posture evolves through ADR-driven changes. Existing ADRs record concrete migration paths for stronger edge and origin controls. Teams can adopt those controls incrementally without redesigning core service architecture.

For the platform architecture context behind these decisions, review the ADR index in [architecture/ADRs.md](../ADRs.md).

## Security control status matrix

This matrix summarizes security-relevant platform controls and maturity state for architecture and procurement review.

Status meaning:

- Implemented: baseline enforced by current platform implementation
- Extensible: hardening supported through code and infrastructure extension in the client-owned fork
- Planned: baseline enhancement tracked on the roadmap

| Control | Status | Notes |
|---|---|---|
| Least-privilege IAM (`no Action: "*"`) | Implemented | Repository-authored baseline enforced; wildcard exceptions are bounded and documented. |
| OIDC-based CI and CD deployment identity | Implemented | GitHub Actions role assumption uses constrained OIDC trust, not long-lived AWS keys. |
| Public edge protection (WAF and public ALB) | Implemented | WAF host filtering, rate limiting, and geo controls at public edge. |
| Internal service authentication and authorization | Implemented | Application-layer JWT validation and service authorization controls are active. |
| Runtime isolation and container non-root baseline | Implemented | Fargate private-subnet deployment with non-root container runtime baseline. |
| Internal traffic encryption (HTTPS and mTLS for service-to-service) | Extensible | Transport hardening available through client-owned code and infrastructure extension. |
| Customer-managed KMS key strategy | Extensible | Supported through client-owned key management and infrastructure extension. |
| ALB access logging baseline | Planned | Tracked as roadmap security enhancement. |
| CloudTrail baseline guidance and enforcement pattern | Planned | Tracked as roadmap security enhancement. |
| SIEM integration reference pattern | Planned | Tracked as roadmap security enhancement. |

## Verified IAM wildcard exceptions inventory

Audit date: 2026-04-27  
Audit scope: repository-authored IAM policy code in `infra/src` constructs and IAM utilities.
Method note: this inventory is derived from repository-level static analysis and should be revalidated as part of platform release review.

### Validation result

- No repository-authored IAM statement uses `Action: "*"` or `NotAction`.
- Wildcards are limited to resource patterns where AWS requires account-scope permissions or where controlled name patterns are required.

### Exceptions

1. ECR authorization token retrieval
   - Location: `infra/src/lib/construct/forge-github-role-construct.ts`
   - Statement: `actions: ['ecr:GetAuthorizationToken']`, `resources: ['*']`
   - Rationale: AWS ECR token retrieval is account-scoped and does not support repository ARN scoping.

2. ECR authorization token helper utility
   - Location: `infra/src/lib/utils/iam-utils.ts`
   - Statement: `actions: ['ecr:GetAuthorizationToken']`, `resources: ['*']`
   - Rationale: Shared utility that mirrors AWS ECR account-scoped requirement.

3. SES send and account send-status checks
   - Location: `infra/src/lib/construct/forge-ecs-task-role-construct.ts`
   - Statement: `actions: ['ses:GetAccountSendingEnabled', 'ses:SendEmail', 'ses:SendRawEmail']`, `resources: ['*']`
   - Rationale: SES actions used by the platform are account-scoped in IAM and are not resource-ARN addressable for narrower scoping.

4. Cognito integration test secrets namespace
   - Location: `infra/src/lib/construct/cognito/forge-cognito-gha-policy-construct.ts`
   - Statement: `actions: ['secretsmanager:GetSecretValue']`, `resources: ['arn:aws:secretsmanager:<region>:<account>:secret:forge/cognito/*']`
   - Rationale: Prefix scoping is used to allow CI access only to a constrained Cognito-related secret namespace.

5. Service account and client secret suffix matching
   - Location: `infra/src/lib/construct/forge-ecs-task-role-construct.ts`
   - Pattern: wildcard suffixes are used for Secrets Manager ARNs generated with AWS secret name suffixing.
   - Rationale: Secrets Manager secret ARNs include generated suffix components, so deterministic prefix or suffix matching is required for stable least-privilege policy behavior.

### Ongoing review expectation

Any new IAM wildcard usage must include one of the following:

- an AWS API constraint that prevents narrower resource scoping, or
- a deterministic naming constraint with bounded prefix or suffix matching.

Each case should include an inline rationale and corresponding cdk-nag suppression reason where applicable.
