---
title: "Runbook"
summary: "This is the operational runbook for the Forge platform. The sections below focus on infrastructure and delivery"
---

This is the **operational runbook** for the Forge platform. The sections below focus on **infrastructure and delivery** (container runtime mode on ECS, GitHub Actions runners for native builds). The same document can be extended with additional runbook procedures over time.

## 1) Native → JVM Escape Hatch

There's an escape hatch if experiencing Quarkus/GraalVM native issues.

### When to use

- Native image is failing (startup errors, crashes, memory issues)
- You need a fast, stable fallback

------------------------------------------------------------------------

### Switch to JVM

Edit GitHub > Settings > Secrets and variables > Actions > Variables > `FORGE_ECS_RUNTIME_MODE` > jvm

------------------------------------------------------------------------

### What this does

- Keeps the same deploy tag: `<serviceName>`
- You should also see a tag `<serviceName>-jvm` in ECR (previous version will have been tagged with `<serviceName>-native`)
- Swaps runtime only (native → JVM)
- No infra or config changes required

------------------------------------------------------------------------

### Rollback (return to native)

Revert the GHA variable to `native`.

------------------------------------------------------------------------

## 2) Self-hosted Mac runner (for native builds)

### When to use

- Native builds are slow or timing out on GitHub-hosted runners
- You need consistent performance for GraalVM builds

------------------------------------------------------------------------

### Start / register runner (5 minutes)

In GitHub:

- Go to **Settings → Actions → Runners → New self-hosted runner**
- Select **macOS**
- Copy the commands

On your Mac, run exactly what GitHub provides:

```bash
mkdir actions-runner && cd actions-runner
curl -o actions-runner.tar.gz -L <url-from-github>
tar xzf actions-runner.tar.gz

./config.sh --url https://github.com/get-forge/forge-core --token <token>
./run.sh
```

**Notes:**

- Add labels when prompted (for example `self-hosted`, `macOS`, `X64`, `forge-ecr`)
- Keep the terminal or session running (or install as a service)

**Requirements (do not skip):**

- Docker or OrbStack running
- `docker` CLI works in the same shell
- Machine must stay awake during builds

------------------------------------------------------------------------

### Use the runner in workflows

Set repo variable **Settings → Actions → Variables**:

`FORGE_ECR_RUNS_ON` = `["self-hosted","macOS","X64","forge-ecr"]`

This routes the ECR build job to your Mac. Remove the variable to revert to `ubuntu-latest`.

------------------------------------------------------------------------

### Verify

In the workflow run:

- Job shows `runs-on: self-hosted`
- Logs execute on your machine (visible locally)

------------------------------------------------------------------------

### AWS / ECR

Uses existing OIDC role `forge-github-actions-ci`. No changes required if hosted runners already work.

------------------------------------------------------------------------

### Stop / disable

- Stop runner: Ctrl+C in the runner terminal
- Remove variable `FORGE_ECR_RUNS_ON` to fall back to GitHub-hosted

------------------------------------------------------------------------

### Notes

- Native builds run inside Docker (Linux), even on macOS
- First run is slower (pulls builder images)
