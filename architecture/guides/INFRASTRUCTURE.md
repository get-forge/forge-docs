# AWS platform infrastructure

Forge provisions AWS infrastructure using the AWS Cloud Development Kit (CDK) in TypeScript.

Forge deploys as a **single logical platform per environment**, with shared infrastructure services and independently
deployable stateless application services.

The platform deploys as a container-native operating model built primarily on:

- ECS Fargate for stateless application workloads
- Application Load Balancers for ingress and routing
- Managed AWS datastores and messaging primitives
- Infrastructure as code for repeatable environment provisioning

Forge environments are designed to be deterministic, reproducible, and operator-owned. Infrastructure is provisioned
directly into AWS accounts controlled by the operator organization; Forge is not a hosted control plane.

This guide explains:

- how Forge infrastructure is structured
- how services become deployable workloads
- how environments are configured
- how stacks are composed and deployed
- baseline cost and availability expectations

Standard CDK lifecycle commands are documented in [CHEATSHEET.md](../../CHEATSHEET.md).

> 💡 **Note:** Forge currently supports deployment within a single AWS region per environment, including multi-AZ
> infrastructure patterns for high availability inside that region.
>
> Multi-region deployment, replication, and failover patterns are planned roadmap areas and are expected to evolve
> alongside enterprise operational requirements. The current single-region posture is intentionally pragmatic for
> most early-stage and mid-scale deployments, where operational simplicity is typically more valuable than cross-region
> complexity.

## Infrastructure design goals

The infrastructure model prioritizes:

- predictable deployments
- stateless horizontal scalability
- least-privilege infrastructure access
- private-by-default runtime networking
- low operational overhead
- progressive hardening for enterprise environments

The default deployment model avoids imposing assumptions about AWS Organizations structure, landing-zone topology, or
centralized enterprise governance tooling.

Operators can extend the baseline architecture to align with their own security, networking, compliance, and organizational requirements.

## Configuration sources

Forge uses a small number of centralized configuration inputs that drive infrastructure synthesis and deployment behavior.

### Deployable services and routing

`infra/src/lib/constant/forge-services.json` defines the services that become ECS workloads.

Each entry specifies:

- module path
- ECS service name
- public or internal exposure
- ALB routing and path rules

CDK uses this file to generate:

- ECS services
- target groups
- listener rules
- internal and public routing behavior

Adding a new deployable service typically starts with a new entry in this file.

### Environment and capacity configuration

`config/src/main/resources/platform-config.yml` contains operator-maintained infrastructure configuration consumed during CDK synthesis.

Typical settings include:

- domain configuration
- feature toggles
- ECS desired counts
- VPC topology
- NAT configuration
- endpoint placement strategy
- stage-specific overrides for DEV, INT, TEST, and PROD

Important infrastructure controls include:

| Setting | Purpose |
|:--------|:--------|
| vpcAzCount | Controls VPC AZ spread (maxAzs). RDS requires at least two AZs for subnet group creation. |
| endpointAzMode | minimal reduces endpoint cost by deploying interface endpoints into fewer AZs; ha spreads endpoints across all private AZs. |
| natEnabled | Enables or disables NAT gateway provisioning. |
| ecsDesiredCount | Default ECS service replica count. |

Use `task bootstrap:platform-config` to edit configuration interactively.

Forge intentionally uses a minimal profile-based AWS configuration model so operators can integrate the platform into
existing AWS environments without restructuring account layouts, IAM strategy, or organizational controls.

See [OPERATIONS.md](../../OPERATIONS.md) for development profile setup.

## Deployment architecture

The CDK entrypoint `infra/src/bin/app.ts` creates:

- one shared ECR stage per account and region
- one API stage per Forge environment (DEV, INT, TEST, PROD, etc.)

Each environment stage is built from `infra/src/lib/stage/forge-api-stage.ts`.

### Shared ECR stage

Forge-ECR provisions the shared forge/platform Amazon ECR repository.

This repository is intentionally long-lived and shared across all Forge environments in the AWS account and region.

Container images are published once and consumed by multiple deployment stages.

### Environment stages

Each Forge environment provisions a complete isolated runtime environment composed of multiple CDK stacks with explicit dependencies.

**1. GitHubRoleStack**

Provisioned **only for INT**, the prescribed integration and CI/CD environment. Other Forge stages omit this stack.

Provides:

- GitHub Actions OIDC integration
- CI/CD IAM roles
- deployment trust relationships

**2. NetworkStack**

Provisions core networking infrastructure:

- VPC
- public and private subnets
- optional NAT gateways
- interface and gateway endpoints
- baseline network controls

**3. DomainStack**

Provisions:

- Route 53 DNS
- ACM certificates
- email and domain-related configuration

**4. CognitoIdpStack**

Creates Cognito user pools for:

- human authentication
- service authentication

Cognito is provisioned only in real AWS environments because LocalStack does not fully emulate Cognito behavior.

**5. DatastoreStack**

Provisions shared platform datastores including:

- PostgreSQL (RDS)
- DynamoDB
- S3

All stateful infrastructure runs inside private networking boundaries.

**6. SecurityStack**

Creates:

- WAF configuration
- security groups
- ECS execution and task roles
- secret integrations
- baseline IAM controls

**7. RuntimeStack**

Deploys the runtime application layer:

- ECS cluster
- Fargate services
- task definitions
- load balancers
- listener rules
- service routing

Service deployment behavior is derived directly from forge-services.json.

### Runtime networking model

Forge workloads run primarily inside private subnets.

Public ingress is restricted to explicitly exposed Application Load Balancers protected by AWS WAF.

The default networking model separates:

- internet-facing edge traffic
- internal application workloads
- managed data infrastructure

This layout supports:

- least-privilege traffic flows
- simplified operational reasoning
- incremental hardening for stricter environments

### Container image model

Forge uses a single shared Amazon ECR repository per account and region:

forge/platform

Each service publishes tagged container images into this repository.

#### Tag strategy

| Tag pattern | Purpose |
|:------------|:--------|
| `<serviceName>` | Mutable deployment tag referenced by ECS task definitions |
| `<serviceName>-<mavenVersion>-<gitShortSha>` | Immutable traceable build tag |
| `<serviceName>-native` / `<serviceName>-jvm` | Records runtime build flavor |

Images are built and published through GitHub Actions workflows.

CI controls whether services deploy as:

- GraalVM native images
- JVM container builds

See [RUNBOOK.md](../../RUNBOOK.md) for runtime switching guidance.

## Adding a deployable service

To add a new deployable service:

1. Add a new service definition to forge-services.json
2. Include the module in the Maven reactor
3. Allow CI to build and publish the container image
4. Redeploy the target environment

RuntimeStack automatically provisions the required ECS service and routing configuration.

Redeploying the shared ECR stage is not required.

## Day-to-day operations

Operational commands and workflows are documented separately:

- [CHEATSHEET.md](../../CHEATSHEET.md) - CDK workflows, deploy/destroy operations, LocalStack usage
- [RUNBOOK.md](../../RUNBOOK.md) - operational runbook (runtime image switching, self-hosted GHA runners)

**Tip:** Native image builds can consume significant GitHub Actions minutes. See the self-hosted GHA runner guidance in [RUNBOOK.md](../../RUNBOOK.md).

## Baseline pricing guide (development and staging)

Typical non-production deployments are relatively inexpensive by enterprise platform standards.

Approximate monthly costs in us-west-2 for a representative non-production footprint:

- seven ECS services
- two ALBs
- WAF
- logging
- secrets management
- DNS

Indicative pricing excludes:

- RDS storage and backup growth
- DynamoDB usage
- NAT gateway charges
- data transfer
- endpoint data processing

Validate all production assumptions with the AWS Pricing Calculator.

### Endpoint cost model

Forge provisions:

- seven interface VPC endpoints
- S3 and DynamoDB gateway endpoints

Gateway endpoints do not incur hourly charges.

Interface endpoints are billed:

- per endpoint
- per Availability Zone
- plus data processing usage

`endpointAzMode: minimal` significantly reduces non-production cost by deploying endpoints into a reduced AZ footprint.

`ha` mode deploys endpoints into all private AZs for higher availability.

This AZ multiplier is often the largest hidden infrastructure cost factor in smaller AWS environments.

| Component | Monthly (approx.) |
|:----------|------------------:|
| Fargate workloads | ~$63 |
| Interface endpoints | ~$50 |
| Two ALBs | ~$32 |
| WAF | ~$7.50 |
| CloudWatch Logs | ~$3.50 |
| Secrets Manager | ~$0.40 |
| Route 53 | ~$0.50 |
| Indicative subtotal | ~$155 |

## Availability expectations

Indicative availability depends heavily on deployment topology.

| Deployment model | Indicative monthly availability | Notes |
|:-----------------|:--------------------------------|:------|
| Single AZ | ~99.5-99.7% | Lower cost baseline with limited fault tolerance |
| Multi-AZ Fargate | ~99.99% | Aligns more closely with AWS managed service availability targets |
| Multi-region | ~99.999%+ | Requires explicit cross-region architecture and operational processes |

Forge supports progressive availability hardening without requiring architectural redesign of application services.
