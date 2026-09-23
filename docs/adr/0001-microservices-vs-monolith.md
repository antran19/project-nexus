# ADR-0001: Full microservices for all six SRS domains

## Status
Accepted

## Context
The project's SRS describes six business domains (User, Notification, Catalog, Commerce,
Auction, Fulfillment). A mentor's reference capacity-planning workbook, sized for a
hypothetical 8-person/8-month team, recommends a modular monolith (five domains sharing
one deployable, separate schemas) with only Auction split out as its own service — citing
"N microservices with too few developers" as a high-severity operational risk.

This project is a solo/small-team learning and portfolio effort, not the mentor's
hypothetical team, with a different goal: demonstrating genuine service boundaries,
independent deployability, and inter-service messaging for a CV, and building deep
enough understanding to explain the architecture to a 4-person student team.

## Decision
Build all six domains as genuinely independent Spring Boot microservices, each with its
own database, each independently deployable, communicating over the network (REST via
the gateway, events via Kafka) rather than in-process module calls.

## Consequences
- More operational surface than a monolith: six services to build, deploy, and keep
  healthy instead of one deployable plus a separate Auction service.
- Requires the full platform investment this plan covers: service discovery, an API
  gateway, and a message broker, before any second business service can be added.
- Each service's implementation cost (its own persistence, its own CI pipeline, its own
  Docker image) is paid six times instead of once — accepted deliberately for the
  CV/learning goal, and mitigated by keeping each service's own scope thin at first
  (this plan's `user-service` implements only register/login/change-password, not the
  full SRS §3.1.1 feature set).
- The mentor's operational-overhead concern does not disappear — it is accepted as a
  known trade-off for a portfolio project rather than a production team under a hard
  deadline, where it would not be an acceptable trade-off.
