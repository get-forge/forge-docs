# Architecture Decision Records (ADRs)

This repository contains a public, sanitized subset of Architecture Decision Records (ADRs) from the Forge Platform.

ADRs are used to document consequential engineering decisions—capturing the context, constraints,
alternatives considered, and the rationale behind chosen approaches. They serve as a durable
record of *why* the system is the way it is.

## Scope and Redaction

Not all ADRs are published.

The records included here:
- Represent decisions relevant to Forge Platform consumers
- Exclude sensitive details (e.g. security mechanisms, internal topology, proprietary workflows)
- May be abstracted or generalized from their original form

## Interpretation Notes

- ADRs are **historical artifacts**, and not reflective of the current implementation
- Decisions may evolve; superseding ADRs will be linked where applicable
- Trade-offs and rejected alternatives are preserved to aid understanding

## ADR Index

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
