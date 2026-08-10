---
name: gcp-ops
description: Use when diagnosing the GCP-hosted backend VM — instance status, container logs, network/firewall checks, billing/cost, or restarting the backend container. Covers how to run these read-mostly ops safely. Enforcement is the guard hook + scoped credentials, not this skill.
---

# GCP ops

Operational know-how for the production backend: a single `e2-micro` VM
(`fashion-rental-backend`, zone `us-central1-a`) running the Spring Boot backend in
Docker, provisioned via `.github/workflows/infra-provision.yml` (see
`infra/deployment-plan.md`).

**This skill is guidance, not a wall.** What is actually permitted is enforced by
`.claude/hooks/guard.py` (which blocks VM/IP/firewall/disk/IAM delete and create,
container removal, and secret reads) and by the viewer-scoped service account the
session runs as. Do not attempt to bypass either.

## What you do

- VM status: `gcloud compute instances describe fashion-rental-backend --zone=us-central1-a`.
- Container logs: `gcloud compute ssh fashion-rental-backend --zone=us-central1-a --command="docker logs --tail 200 fashion-rental-backend"`.
- Container up? `docker ps`. Network/firewall: `gcloud compute firewall-rules list`, `gcloud compute networks describe`. Cost: `gcloud billing accounts list`.
- Restart a hung backend container: `gcloud compute ssh fashion-rental-backend --zone=us-central1-a --command="docker restart fashion-rental-backend"` — the only write action.

## What is off-limits

Deleting/stopping/recreating the VM, static IP, firewall rules, disks, or IAM
identities; removing containers/volumes other than the one restart; reading secrets
(`gcloud secrets`, `docker inspect`, `.env*`, `*_PASSWORD`/`*_SECRET`/`*_KEY`/`*_TOKEN`).
The hook enforces all of this — report the needed action, let the user do it.

## How you report

State what you checked, what you found, and — if something is wrong — your diagnosis
and the minimal next step. Do not speculate about causes you have not checked.
