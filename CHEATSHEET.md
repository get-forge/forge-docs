# Cheatsheet

`task --list` · `task --list-all`

## Bootstrap dev env

Step-by-step explanations, prerequisites, and follow-on setup are in [DEVELOPMENT.md — Environment Setup](DEVELOPMENT.md#environment-setup).

### Tooling

```bash
brew install go-task                               # forge exposes all operational surfaces via taskfiles; install this first
task bootstrap:toolchain                           # install cli toolchain
task bootstrap:mvn                                 # configure maven settings to allow access to GitHub Packages
task lefthook:install                              # install git hooks configured in .config/lefthook.yml
```

### Licensing

```bash
task bootstrap:licence-install                     # install the licence file on your machine
task bootstrap:licence-secure                      # secure the licence file and directory
```

### Configuration

```bash
task bootstrap:platform-config                     # configure interactive platform-config.yml (dev region, DNS, ECS, NAT)
task bootstrap:dotenvrc                            # generate .envrc from API keys and secrets; run direnv allow
```

### Cognito (free tier AWS; unsupported in LocalStack)

```bash
task cdk:synth
task aws:deploy-cognito                            # deploy Cognito Stack
task bootstrap:cognito                             # refresh Cognito pool ids in .envrc (post aws:deploy-cognito)
```

## Bootstrap GitHub env

Commentary, prerequisites, and follow-on setup are in [OPERATIONS.md — GitHub Setup](OPERATIONS.md#github-setup).

```bash
FORGE_STAGE_ENV=INT task cdk:synth
FORGE_STAGE_ENV=INT task aws:deploy-github-role    # deploy GitHub OIDC role
```

## Local development

```bash
task docker:restart -- localstack jaeger postgres  # restart listed docker containers
task docker:start -- localstack jaeger postgres
task docker:stop -- localstack jaeger postgres
task docker:status                                 # docker container status and ports for all services

task dev:localstack                                # create LocalStack development resources
task seed:localstack                               # seed LocalStack development resources
task seed:cognito                                  # seed AWS Cognito test users
task seed:postgres                                 # sync Cognito test users with LocalStack Postgres

task build:nuke                                    # delete the Maven build cache + stop mvn daemons
task build:clean
task build:compile
task build:package
task build:install

task test:unit
task test:integration
task test:integration MODULE=services/auth-service # run a specific module's integration tests
task test:all                                      # run all unit and integration tests
task test:static                                   # OWASP, PMD, SpotBugs, Checkstyle static code analysis

mert start                                         # run all quarkus dev services locally

task dev:audit                                     # run a single quarkus dev service locally
task dev:auth
task dev:actor
task dev:document
task dev:notification
task dev:bff                                       # actor-bff BFF application
task dev:web                                       # web-actor static/stateless ui

task quarkus:status                                # local quarkus server port contention
task quarkus:kill                                  # kill all local quarkus servers

task metrics:restart                               # restart Prometheus + Grafana; regenerate configs and dashboards
task metrics:start
task metrics:stop

task metrics:prometheus-start
task metrics:prometheus-stop
task metrics:prometheus-config
task metrics:grafana-start
task metrics:grafana-stop
task metrics:grafana-dashboard
```

## Infra development

### Standard CDK lifecycle

```bash
task cdk:build
task cdk:test
task cdk:synth
task cdk:diff
```

### AWS env development

```bash
task cdk:install                                   # npm install in infra/ (CI and real AWS)
task aws:bootstrap                                 # CDK toolkit; provision AWS env targeted by forge-sandbox profile

task aws:deploy-all                                # deploy all stacks (AWS)
task aws:deploy-ecr                                # deploy ECR stack (INT)
task aws:deploy-cognito                            # deploy Cognito (INT needs GitHubRole first)
task aws:deploy-domain                             # deploy Domain stack (requires manual DNS updates)

task aws:destroy-all                               # destroy all stacks
task aws:destroy-runtime                           # destroy Runtime stack (where most of the AWS cost is)
```

### LocalStack local development

CDK development is predominantly done directly in AWS free tier.  
However, LocalStack CDK is supported and can be used for development and testing.

```bash
task cdk:install                                   # install Node dependencies and verify cdklocal
task cdk:bootstrap                                 # LocalStack cdklocal bootstrap

task cdk:deploy-all                                # deploy all stacks (LocalStack)
task cdk:deploy-ecr
task cdk:deploy-domain
task cdk:deploy-network
task cdk:deploy-datastore
task cdk:deploy-security
task cdk:deploy-runtime

task cdk:destroy-all
task cdk:destroy-runtime
```
