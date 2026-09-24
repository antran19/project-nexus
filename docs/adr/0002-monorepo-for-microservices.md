# ADR-0002: Monorepo, not one repository per service

## Status
Accepted

## Context
ADR-0001 commits to six independently deployable services. The two common ways to
organize their source are one Git repository per service (polyrepo) or all of them in a
single repository (monorepo). This project is built by one person; cross-cutting changes
(a shared library used by every service, such as `common-core`'s exception hierarchy)
are far more common early on than changes isolated to one service.

## Decision
Use a single Git repository containing all services and shared libraries, organized as a
Maven multi-module build (see the design spec's "Repository structure"). Independence at
deployment time is proven by tooling, not by repository count: each service has its own
GitHub Actions workflow triggered only by changes under its own path (plus `libs/**` and
the root `pom.xml`), and each publishes its own independently versioned Docker image
(tagged by git SHA) to the container registry.

This mirrors how large organizations (Google, Uber, and others) run genuine
microservices out of a monorepo — the repository boundary and the deployment boundary
are independent decisions.

## Consequences
- A single commit can span multiple services' code, which is what makes solo
  cross-cutting changes fast — the reason this decision was made.
- Anyone inspecting only the repository count (not the CI configuration or the deployed
  images) could mistake this for a monolith; this is called out explicitly wherever the
  distinction matters (design spec, this ADR) so it can be explained rather than
  discovered as a surprise.
- If the project ever needed genuinely separate access control per service (e.g.
  different teams with different repo permissions), this decision would need revisiting
  — not a concern for a solo project.
