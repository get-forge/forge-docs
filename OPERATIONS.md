# Operations

Tasks that sit outside day-to-day service development: local metrics (Prometheus and Grafana),
CDK against AWS or LocalStack, and one-time GitHub Actions OIDC bootstrap.

For machine setup and running Quarkus locally, see [DEVELOPMENT.md](DEVELOPMENT.md).

For a compact task index, see [CHEATSHEET.md](CHEATSHEET.md).

## Table of contents

- [GitHub Setup](#github-setup)
- [AWS Setup](#aws-setup)
- [CDK and AWS](#cdk-and-aws)
- [CDK and LocalStack](#cdk-and-localstack)
- [Local metrics](#local-metrics)

---

## GitHub Setup

Forge Platform pipelines run on free tier GitHub plans.  
All workflows run in under 10 minutes. Paid business plans will allow you to optimize runners and reduce build times further.

### 1. Setup AWS IAM resources for GitHub Actions OIDC

First-time (only) INT deployment of stack `Forge-INT/GitHubRoleStack` must occur outside GitHub.  
Workflows call `aws-actions/configure-aws-credentials` a role that is only created when CDK deploys `Forge-INT/GitHubRoleStack`.  
There is no CDK pattern that avoids this: **something** must authenticate to AWS before the role exists.  
Once per account/region, from a machine already logged into the **INT** AWS account (SSO, IAM user, or similar), deploy
only the role stack:

```bash
FORGE_STAGE_ENV=INT task cdk:synth
FORGE_STAGE_ENV=INT task aws:deploy-github-role
```

After that, GitHub Actions can assume the role and GitHub Actions workflows will succeed.  
Only the **INT** stage defines `GitHubRoleStack`; other stages do not create this role.

### 2. Setup GitHub Actions Variables and Secrets

**Required GitHub Actions Variables** (Settings > Secrets and variables > Actions > Variables > Repository variables):

- `AWS_REGION` - AWS region for deployment
- `AWS_ACCOUNT_ID` - AWS account ID for deployment
- `USER_FORGE_DEPLOY` - GitHub username for the account that owns `secrets.PAT_FORGE_DEPLOY` (workflows pass it as `GITHUB_MAVEN_USERNAME` to `configure-maven-github.sh`)

**Required GitHub Actions Secrets** (Settings > Secrets and variables > Actions > Secrets > Repository secrets):

Forge-related secrets:
- `PAT_FORGE_DEPLOY` - GitHub token for deployment
- `FORGE_LICENCE` - Forge licence key for runtime deployment
- `SMALLRYE_CONFIG_SECRET_KEY` (base64 encoded) - Encryption key for secrets.properties
- `GPG_PRIVATE_KEY` - GPG private key for signing artifacts during releases; see [03-release-bump.yml](../.github/workflows/03-release-bump.yml)
- `GPG_PASSPHRASE` - GPG passphrase for signing artifacts during releases

External third-party secrets:
- `NVD_API_KEY` - NVD API key for vulnerability scanning; see [NIST - Request an API Key](https://nvd.nist.gov/developers/request-an-api-key)
- `OSS_INDEX_API_KEY` - OSS Index API key for dependency scanning; see [OSS Index - Auth Required](https://ossindex.sonatype.org/doc/auth-required)
- `OSS_INDEX_USER` - OSS Index username
- `CODECOV_TOKEN` - Codecov token for coverage reporting; see [Codecov](https://codecov.io/)
- `LOCALSTACK_AUTH_TOKEN` - LocalStack auth token for integration tests; see [LocalStack - Auth Token](https://docs.localstack.cloud/aws/getting-started/auth-token/)

Optional secrets:
If you wish to retain frontend ability to 'Login with LinkedIn' you must specify:
- `LINKEDIN_OAUTH2_CLIENT_ID` - LinkedIn Oauth2 client ID; see [LinkedIn - OAUTH 2.0 Overview](https://learn.microsoft.com/en-gb/linkedin/shared/authentication/authentication)
- `LINKEDIN_OAUTH2_CLIENT_SECRET` - LinkedIn Oauth2 client secret
- `LINKEDIN_REFRESH_TOKEN_ENCRYPTION_KEY` - Encryption key for LinkedIn refresh token

---

## AWS Setup

Forge Platform development makes extensive use of LocalStack (Docker).
AWS Cognito is unsupported in LocalStack however and requires AWS proper; this is available on AWS free tier.

### 1. Development Environment `forge-sandbox` profile

Because AWS environments vary widely across clients — especially in enterprise contexts — this project relies on a
minimal, profile-based configuration (access key, secret, and region) to avoid imposing assumptions about account
structure, IAM policies, or organizational setup.

In `~/.aws/credentials`, add a profile named `forge-sandbox` with the following contents:

```bash
[forge-sandbox]
aws_access_key_id=********************
aws_secret_access_key=********************
region=us-west-2
```

For greenfield teams or startups without established AWS conventions, it is recommended to bootstrap your AWS
environments using [Superwerker](https://github.com/superwerker/superwerker), developed by AWS Advanced Partners.
Superwerker provides a well-architected baseline with sensible defaults around multi-account structure, security
boundaries, and blast radius management.

---

## CDK and AWS

### Standard CDK lifecycle

```bash
task cdk:build
task cdk:test
task cdk:synth
task cdk:diff
```

### AWS environment

GitHub Actions needs GitHubRoleStack to exist first; bootstrap it manually once (see [GitHub Setup](#1-setup-aws-iam-resources-for-github-actions-oidc)).

DEV is the usual stage for local CDK defaults; CI/CD runs with `FORGE_STAGE_ENV=INT` (see [01-infra-bootstrap.yml](../.github/workflows/01-infra-bootstrap.yml)).

Use this section’s `task aws:*` tasks for AWS CDK deploys — the same shape as CI.
Use [CDK and LocalStack](#cdk-and-localstack) for LocalStack-only CDK development.

```bash
task cdk:install                                   # npm install in infra/ (CI and real AWS)
task aws:bootstrap                                 # CDK toolkit; provision AWS env targeted by forge-sandbox profile

task aws:deploy-all                                # deploy all stacks (AWS)
task aws:deploy-ecr                                # deploy ECR stack (INT)
task aws:deploy-cognito                            # deploy Cognito (INT needs GitHubRole first)
task aws:deploy-domain                             # deploy Domain stack (requires manual DNS updates)
```

---

## CDK and LocalStack

CDK work is usually done against AWS free tier. LocalStack CDK is supported for development and testing.

```bash
task cdk:install                                   # install Node dependencies and verify cdklocal
task cdk:bootstrap                                 # LocalStack cdklocal bootstrap

task cdk:deploy-all                                # deploy all stacks (LocalStack)
task cdk:deploy-ecr
# task cdk:deploy-cognito intentionally omitted as unsupported in LocalStack
task cdk:deploy-domain
task cdk:deploy-network
task cdk:deploy-datastore
task cdk:deploy-security
task cdk:deploy-runtime
```

## Local metrics
Prometheus and Grafana run in the same local Docker stack as LocalStack, Jaeger, and Postgres (not emulated AWS services).

**IaC for Metrics is a priority on the Release Roadmap.** See [ROADMAP.md](../ROADMAP.md)

```bash
task metrics:restart                               # restart Prometheus + Grafana; regenerate configs and dashboards
task metrics:start
task metrics:stop
```

---
