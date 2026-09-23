# Sub-project #1: Platform Foundation + User Service — Design

**Date:** 2026-09-22
**Author:** Trần Nguyễn Minh An (leader, personal solo build)
**Status:** Approved by user, pending professor confirmation on overall project scope

## Context

Project Nexus is a course capstone (FPT), team of 4 (Trịnh Hoàng Mai Anh, Vũ Thị Tú Anh,
Trần Nguyễn Minh An — leader, Phan Anh Khoa), built from an SRS ("Project Nexus SRS v1.0",
04/06/2026, author HauNK) describing an e-commerce + auction marketplace. The professor
confirmed the domain: e-commerce with auction.

This is a personal, solo effort by the leader ahead of team Sprint 0, for two goals:
1. Build deep enough understanding of the architecture to explain it to the other 3
   team members later.
2. Produce a portfolio-worthy project for the leader's CV — favoring breadth of
   demonstrated technology (real microservices, service discovery, API gateway,
   event-driven messaging, containerization, CI/CD, observability) over minimal scope.

A mentor (Tuấn Nghiêm) shared a reference capacity-planning workbook
(`Nexus-Backlog-Plan.xlsx`) sized for a hypothetical 8-person, 8-month team. It is a
reference only, not the team's actual plan. Its architecture recommendation
(ADR-002: modular monolith + a separately-scaled Auction service) was considered and
explicitly **overridden** — the user chose full microservices for all 6 domains, for
CV/learning breadth, accepting the added implementation cost this creates.

The project is too large for one spec. This document covers only the **first
sub-project**: the shared platform infrastructure plus the first working business
service (User Service), which together prove the whole architecture end-to-end.

## Goal of this sub-project

By the end of this sub-project, running `docker-compose up` locally should produce a
working flow: a client can register, log in, and receive a JWT, with the request
passing through the API Gateway, resolved via service discovery, processed by
User Service, persisted to its own database, and an event published reliably to Kafka
— proving every piece of the shared platform works before any other business service
is built on top of it.

## Scope

**In scope:**
- Repository structure and build setup (monorepo, Maven multi-module)
- `discovery-server` (Eureka)
- `api-gateway` (Spring Cloud Gateway): routing, JWT signature/expiry validation
- `user-service`: register, login, change password, publish domain events
- Shared libraries: `common-core`, `common-security`, `common-events`, `common-web`
- Outbox pattern for reliable event publishing (user-service → Kafka)
- Per-service Dockerfile + root `docker-compose.yml` (Postgres, Kafka in KRaft mode,
  discovery-server, api-gateway, user-service)
- Per-service CI pipeline (path-triggered) + independent Docker image versioning
- ADRs: microservices vs monolith (override of mentor's ADR-002, with rationale),
  monorepo-for-microservices (citing Google/Uber precedent + per-service CI as the
  mitigation)
- Health checks (Spring Boot Actuator) per service
- Structured JSON logging (console/file, no centralized log aggregation yet)

**Out of scope for this sub-project (deferred to later sub-projects):**
- The other 5 business services (Catalog, Commerce, Auction, Fulfillment, Notification)
- Kubernetes manifests / actual K8s deployment
- Centralized observability (OpenTelemetry, Prometheus, Grafana)
- Config server (centralized configuration)
- Schema registry for Kafka (Avro/Protobuf) — plain JSON events for now
- Full RBAC role/privilege management UI (only enough of the Role/Privilege data model
  to resolve a user's privileges at login)

## Repository structure

Monorepo, Maven multi-module build:

```
project-nexus/
├── pom.xml                        # parent POM
├── docker-compose.yml
├── .github/workflows/
│   ├── user-service-ci.yml        # path-triggered: services/user-service/**
│   ├── api-gateway-ci.yml
│   └── discovery-server-ci.yml
├── docs/
│   ├── adr/
│   │   ├── 0001-microservices-vs-monolith.md
│   │   └── 0002-monorepo-for-microservices.md
│   └── api/                       # OpenAPI spec export per service
├── infra/
│   ├── docker/
│   └── scripts/
├── libs/
│   ├── common-core/                # exceptions, response wrapper, utils
│   ├── common-security/            # JWT filter, @RequiresPrivilege annotation
│   ├── common-events/              # Kafka event DTOs/schemas
│   └── common-web/                 # global exception handler, response format
├── platform/
│   ├── discovery-server/
│   └── api-gateway/
└── services/
    └── user-service/
        ├── pom.xml
        ├── Dockerfile
        ├── src/main/java/com/nexus/user/
        │   ├── api/                # controllers, request/response DTOs
        │   ├── application/        # use cases
        │   ├── domain/             # entities, domain events
        │   ├── infrastructure/     # JPA repos, Kafka outbox publisher, config
        │   └── UserServiceApplication.java
        ├── src/main/resources/
        │   ├── application.yml
        │   └── db/migration/       # Flyway
        └── src/test/java/...
```

Rationale for monorepo (not one repo per service): solo developer, so cross-cutting
changes (shared library updates) must be one commit, not six. Independence at
deployment time is preserved via per-service CI pipelines (path-triggered) and
independent Docker image tags per service — documented in ADR-0002.

## Internal layering (applies to every service, not just user-service)

Each service follows Clean/Hexagonal Architecture, expanding the 4 top-level packages
above into concrete layers. Shown here for `user-service`; every future service
(Catalog, Commerce, Auction, Fulfillment, Notification) follows the same shape.

```
com.nexus.user/
├── api/
│   ├── UserController.java
│   ├── AuthController.java
│   ├── dto/
│   │   ├── request/RegisterUserRequest.java
│   │   └── response/UserResponse.java
│   └── mapper/UserApiMapper.java           # DTO <-> domain (MapStruct)
│
├── application/
│   ├── usecase/
│   │   ├── RegisterUserUseCase.java
│   │   ├── LoginUseCase.java
│   │   └── ChangePasswordUseCase.java
│   ├── port/out/                           # interfaces application depends on
│   │   ├── UserRepositoryPort.java
│   │   ├── PasswordHasherPort.java
│   │   └── EventPublisherPort.java
│   └── exception/DuplicateEmailException.java
│
├── domain/                                 # no Spring/JPA imports allowed here
│   ├── model/User.java                     # plain Java, not @Entity
│   ├── model/Role.java, Privilege.java
│   ├── event/UserRegisteredEvent.java
│   └── service/PasswordPolicy.java
│
├── infrastructure/                         # implements the port interfaces
│   ├── persistence/
│   │   ├── entity/UserJpaEntity.java       # @Entity — kept separate from domain.model.User
│   │   ├── UserJpaRepository.java          # Spring Data interface
│   │   └── UserRepositoryAdapter.java      # implements UserRepositoryPort
│   ├── messaging/
│   │   ├── OutboxEventPublisherAdapter.java  # implements EventPublisherPort
│   │   └── OutboxRelayJob.java             # scheduled: outbox table -> Kafka
│   ├── security/BCryptPasswordHasherAdapter.java
│   └── config/SecurityConfig.java, KafkaConfig.java
│
└── UserServiceApplication.java
```

**Dependency direction (inward only):**
```
api -> application -> domain
infrastructure -> application (implements its port interfaces) -> domain
```
`domain` depends on nothing — no Spring, no JPA, no Kafka. Only `infrastructure` knows
which concrete technology is behind each port.

`domain.model.User` (plain) is kept separate from
`infrastructure.persistence.entity.UserJpaEntity` (`@Entity`-annotated) specifically so
business logic never carries a persistence-framework dependency; `UserRepositoryAdapter`
is the only place that maps between the two. This "port in application, adapter in
infrastructure" split is the Dependency Inversion Principle applied directly — it is
also what makes `RegisterUserUseCase` unit-testable with a mocked
`UserRepositoryPort`, no Spring context or database required.

## Runtime components (local, docker-compose)

| Component | Port | Role |
|---|---|---|
| `discovery-server` (Eureka) | 8761 | Service registry |
| `api-gateway` | 8080 | Single entry point |
| `user-service` | 8081 | First business service |
| Postgres (`user_db`) | 5432 | user-service's own database |
| Kafka (KRaft mode) | 9092 | Event bus |

Each service registers itself with Eureka on startup and is looked up by the gateway
by service name (not hardcoded host:port).

## Data flow

**Register:**
1. `POST /api/v1/users/register` → api-gateway (public route, no JWT required)
2. Gateway resolves `user-service` via Eureka, forwards the request
3. user-service validates input, hashes the password, persists the user record
4. In the same DB transaction, writes a `UserRegistered` event row to an outbox table
5. A separate outbox-poller process reads unpublished rows and publishes them to the
   Kafka topic `user-events`, marking them published on success, retrying with backoff
   on failure — this decouples "user saved" from "Kafka is currently reachable"
6. Response returns through the gateway to the client

**Login:**
1. `POST /api/v1/auth/login` → gateway → user-service
2. user-service verifies credentials
3. user-service resolves the caller's privileges (via Role → Privilege mapping in its
   own database) and issues a JWT containing `userId`, `role`, and a `privileges` claim
   (list of privilege codes, e.g. `["USER.CREATE", "ORDER.VIEW"]`), expiring per
   `ACCESS_TOKEN_EXPIRY_MINUTES` (default 60, per SRS config table)

**Authenticated request (pattern every future service will reuse):**
1. Client sends `Authorization: Bearer <JWT>`
2. api-gateway verifies the JWT's signature and expiry only (coarse check) — invalid
   or missing token is rejected with 401 before reaching any service
3. The receiving service (user-service here; every other service later) checks the
   `privileges` claim against what the specific endpoint requires, via a
   `@RequiresPrivilege("...")` annotation supplied by `common-security`

## Authentication vs. authorization (explicit split)

- **Authentication** (who): user-service issues the JWT; api-gateway validates the
  JWT's signature/expiry only.
- **Authorization** (what they can do): resolved at login time into the JWT's
  `privileges` claim (supports the SRS's custom-role model, not just 4 fixed roles —
  see SRS 3.1.1 Roles Management and the Privileges List), and enforced by each
  service individually via `common-security`'s annotation. The gateway does not make
  authorization decisions — it has no knowledge of individual services' business
  rules.
- **Known trade-off:** a privilege change made by an admin does not take effect for an
  already-issued JWT until it expires (bounded by `ACCESS_TOKEN_EXPIRY_MINUTES`).
  Accepted for this sub-project; revisit only if a future requirement demands
  immediate revocation.

## Error handling

- Standardized JSON error response shape, defined once in `common-web`, reused by
  every service
- Gateway returns 503 if Eureka has no healthy instance for the requested service
- user-service: 400 with field-level messages for validation errors, 409 for duplicate
  email
- Outbox publisher retries failed Kafka publishes with backoff; failures are logged,
  never silently dropped, and never fail the original HTTP request

## Testing strategy

- **Unit tests:** pure domain logic (password hashing, validation rules) — plain
  JUnit, no Spring context
- **Integration tests:** Testcontainers spin up real Postgres + Kafka to verify the
  repository and outbox-to-Kafka flow actually works, not just against mocks
- **Manual smoke test:** `docker-compose up`, then register → login → confirm the
  issued JWT is valid and carries the expected claims

## Key decisions (to be recorded as ADRs)

- **ADR-0001:** Full microservices for all 6 SRS domains, overriding the mentor's
  reference recommendation (modular monolith + separate Auction service). Rationale:
  the project is a solo/small-team learning + portfolio effort where demonstrating
  genuine service boundaries, inter-service messaging, and independent deployability
  outweighs the operational-overhead concern that applies to a real 8-person team
  under a hard deadline.
- **ADR-0002:** Monorepo, not one repository per service. Rationale: solo development
  velocity; independence preserved via per-service CI pipelines and independent Docker
  image versioning, not via repository count.

## Open questions / risks carried forward

- **Scope risk:** the mentor's reference plan flags "N microservices with too few
  developers" as a high-severity risk even for an 8-person team. A solo/4-person
  student team taking on all 6 services plus full platform infrastructure carries
  the same risk, magnified. Recommendation: confirm with the professor whether the
  full 6-service scope is expected to be fully demoed, or whether a smaller fully-
  working slice plus documented design for the rest is acceptable. This does not
  block starting this sub-project, but should be resolved before committing to
  building all 6 services to the same depth.
