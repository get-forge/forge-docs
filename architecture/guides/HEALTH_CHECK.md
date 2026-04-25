# Health checks

## Quarkus

- **Liveness** and **readiness** via SmallRye Health (`/q/health`, `/q/health/live`, `/q/health/ready`).
- **ECS / ALB**: target group health check path is **`/q/health/ready`** (see
  [`forge-elb-target-group-construct.ts`](https://github.com/get-forge/forge-platform/blob/main/infra/src/lib/construct/forge-elb-target-group-construct.ts)).

## forge-kit: forge-health-aws

Abstract checks live in **get-forge/forge-kit** — package `io.forge.kit.health.impl.infrastructure`:

- `PostgresHealthCheck`
- `DynamoDbHealthCheck`
- `S3HealthCheck`
- `CognitoHealthCheck`

Repository: [forge-impl/forge-health-aws](https://github.com/get-forge/forge-kit/tree/main/forge-impl/forge-health-aws).

## This repository: `*ServiceHealthChecks`

Each service exposes `@Produces` `@Readiness` methods returning anonymous subclasses of the kit checks, for example:

| Service                  | [`.../health/`](https://github.com/get-forge/forge-platform/tree/main/services) | Checks (from code)                                                                            |
|--------------------------|------------------------------------|-----------------------------------------------------------------------------------------------|
| **auth-service**         | `AuthServiceHealthChecks`          | Cognito **actor** and **service** user pools via SSM-resolved pool IDs.                       |
| **actor-service**        | `ActorServiceHealthChecks`         | Postgres `forge` / `actors` table.                                                            |
| **document-service**     | `DocumentServiceHealthChecks`      | S3 bucket `forge-documents`, DynamoDB `DOCUMENTS`.                                            |
| **notification-service** | `NotificationServiceHealthChecks`  | Postgres table `notifications`, DynamoDB `NOTIFICATION-TEMPLATES`, SES provider health check. |
| **audit-service**        | `AuditServiceHealthChecks`         | Postgres table `audit_events`.                                                                |

Exact table/bucket names: read the corresponding `*ServiceHealthChecks` source file.
