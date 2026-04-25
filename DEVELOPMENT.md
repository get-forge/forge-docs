# Developer Setup Guide

A comprehensive guide for setting up and working with the Forge platform.

For GitHub and AWS setup, CDK in both LocalStack (development) and AWS (upstream environments) and local Prometheus and Grafana, see [OPERATIONS.md](OPERATIONS.md).

For a compact task index, see [CHEATSHEET.md](CHEATSHEET.md).

For **verified** platform capabilities see [architecture/FEATURES.md](architecture/FEATURES.md); for how-to
guides (auth, metrics, health, …) and the ADR list see [README.md](README.md) (sections **Guides** and **Architecture Decision Records**).

## Table of Contents

- [Environment Setup](#environment-setup)
- [Build & Deploy](#build--deploy)
- [Troubleshooting](#troubleshooting)
- [Useful Debugging Commands](#useful-debugging-commands)
- [Operations (GitHub, IaC, metrics)](OPERATIONS.md)

---

## Environment Setup

### 1. Required Tooling

Forge is an opinionated platform. It has mature operational tooling, and all operations are exposed as taskfile tasks.  
First, install task and then install the remaining toolchain before continuing:

```bash
brew install go-task
task bootstrap:toolchain
```

### 2. Maven settings

To allow consumption of GitHub packages published by the public `get-forge/forge-kit` repo, configure local `~/.m2/settings.xml` with GitHub credentials.
The script prompts for your GitHub username and a classic (only) PAT token named `PAT_FORGE_DEPLOY` that you will need to create.
The script will back up any existing `~/.m2/settings.xml` file first.

```bash
task bootstrap:mvn
```

See [Authenticating with a personal access token][github-pat-auth].  

[github-pat-auth]: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-with-a-personal-access-token

### 3. Git Hooks

Install git hooks using lefthook:

```bash
task lefthook:install
```

This installs git hooks configured in `.config/lefthook.yml`.

### 4. Install and secure the licence file

Create the config directory and place the file at the fixed path:

```bash
task bootstrap:licence-install
```

Secure both the client host forge group/user and the licence file and directory, so only the forge group (and root) can read it.

```bash
task bootstrap:licence-secure
```

Add each user who will run the app to the forge group, then have them **log out and back in** (a new terminal is not enough on macOS).  
See the message printed by the script for the exact commands.

After that, running the app as that user is enough; the process can read the licence via group membership.

### 5. Client platform configuration

To override any default platform configuration values in `config/src/main/resources/platform-config.yml` run:

```bash
task bootstrap:platform-config
```

This script sets up overall:
- **AWS region** for local Quarkus development (only): defaults to `us-west-2`
- **Root domain**: You must set this to your own domain name where you control the DNS.

And defaults for:
- **NAT Gateway enabled**: Set to false everywhere for cost optimization (forge uses VPC endpoints internally);
 you only need this enabled if your platform architecture dictates outbound internet access.
- **ECS desired task count**: Set to 1 for cost optimization.

You can override these values on a per-environment basis.
By convention, forge treats DEV/INT as non-production environments and TEST/PROD as production-like environments.

### 6. Development environment Variables (.envrc)

Create an initial set of environment variables for API keys and secrets:

```bash
task bootstrap:dotenvrc
```

This script sets up, and you will be prompted for:
- **API Keys**: NVD, OSS Index, LinkedIn
- **LocalStack**: Auth Token
- **Quarkus SmallRye Config**: A secret for property encryption; generate using e.g. `openssl rand -base64 32`

**References**:
- OSS Index API key: <https://ossindex.sonatype.org/doc/auth-required>
- NVD API key: <https://nvd.nist.gov/developers/request-an-api-key>
- LocalStack Auth Token: <https://docs.localstack.cloud/aws/getting-started/auth-token/>
- LinkedIn Authentication [optional]: <https://learn.microsoft.com/en-gb/linkedin/shared/authentication/authentication>

### 7. AWS Cognito

LocalStack does not fully support AWS Cognito.
You will need to provision AWS Cognito resources in your development sandbox AWS account.
This enables localhost Quarkus to talk to Cognito while other dependencies are LocalStack/Docker.
This is fully supported in the AWS free tier:

```bash
task cdk:synth
task aws:deploy-cognito
```

Update .envrc with AWS Cognito params from SSM, created by the Cognito stack you just deployed:

```bash
task bootstrap:cognito
```

### 8. LocalStack Docker Services

Start required Docker services for local development:

#### Service management

Services can be stopped, started, and restarted individually or all at once:

```bash
task docker:restart -- localstack jaeger postgres
task docker:start -- localstack jaeger postgres
task docker:stop -- localstack jaeger postgres
```

Service status can be queried with:

```bash
task docker:status
```

Example output:

```terminaloutput
task: [docker:status] scripts/docker/status.sh localstack jaeger postgres prometheus grafana

Docker Container Status

SERVICE      | STATE      | PORTS

localstack   | running    | 4566:4566
jaeger       | running    | 16686:16686,4317:4317
postgres     | running    | 5432:5432
prometheus   | running    | 9090:9090
grafana      | running    | 3000:3000
```

#### Provision LocalStack resources

Provision LocalStack AWS resources (S3 buckets, DynamoDB tables, etc.), and seed development data:

```bash
task dev:localstack
task seed:localstack
```

### 9. Test users [optional]

Once AWS Cognito is provisioned, copy test user data to the Cognito user pool `forge-actor-pool`.
This ensures authentication testing can occur with predefined test users:

```bash
task seed:cognito
```

Once AWS Cognito is provisioned, sync those users into the LocalStack Postgres `ACTORS` table (Cognito holds credentials; Postgres holds actor metadata).
This ensures performance testing login can occur with existing test users:

```bash
task seed:postgres
```

**Prerequisites**:
Service `actor-service` needs to have been started to create the `ACTORS` table (via Flyway) in Postgres.

```bash
# run all quarkus dev services
mert start
# run actor-service only
task dev:actor
```

---

## Build & Deploy

### Build Tasks

Taskfile provides convenient wrappers around the standard Maven commands:

```bash
# Display all available tasks
task

task build:nuke                                    # Delete the Maven build cache + stop mvn daemons
task build:clean
task build:compile
task build:package
task build:install

task test:unit
task test:integration
task test:integration MODULE=services/auth-service # Run a specific module's integration tests
task test:all                                      # Run all unit and integration tests
task test:static                                   # OWASP, PMD, SpotBugs, Checkstyle static code analysis
```

### Development Runtime

Run services in Quarkus dev mode:

#### Services/Applications/UIs

```bash
mert start                                         # Run all quarkus dev services locally

task dev:audit                                     # Run a single quarkus dev service locally
task dev:auth
task dev:actor
task dev:document
task dev:notification
task dev:bff                                       # backend-actor BFF application
task dev:web                                       # web-actor static/stateless ui
```

#### Quarkus Servers

Check the status of running Quarkus servers:

```bash
task quarkus:status
```

Kill any running Quarkus server processes:

```bash
task quarkus:kill
```

## Troubleshooting

### Basic Checks

1. **Docker services not starting**
   - Check Docker/OrbStack is running
   - Verify ports are not in use: `task docker:status`

2. **Quarkus port conflicts**
   - Check running Quarkus servers: `task quarkus:status`
   - Kill conflicting processes: `task quarkus:kill`

## Useful Debugging Commands

### LocalStack

1. notification-service:      ➜ `awslocal ses verify-email-identity --email hello@forgeplatform.software`
2. actor-service:             ➜ `docker exec -it postgres psql -U postgres -d forge -c "SELECT * FROM actor.actors;"`
3. document-service:          ➜ `awslocal dynamodb describe-table --table-name DOCUMENTS 2>&1 | grep -A 10 "KeySchema"`
4. authenticate Docker to ECR ➜

```bash
aws ecr get-login-password --region "$AWS_REGION" \
| docker login --username AWS --password-stdin "$(echo $ECR_URI | cut -d/ -f1)"
```
