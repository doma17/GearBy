# Backend test strategy

## TDD loop

For each behavior, add a failing test in the smallest applicable layer, make it pass, then refactor without changing the contract. Run the owning tag while iterating; run `allTest` before merging.

## Gradle suites

| Task | JUnit tag | Scope |
| --- | --- | --- |
| `./gradlew unitTest` | `unit` | Fast controller slices, service rules, and security configuration. |
| `./gradlew integrationTest` | `integration` | PostgreSQL Testcontainers, Flyway migrations, repositories, and end-to-end feature flows. |
| `./gradlew contractTest` | `contract` | OpenAPI-facing status, authentication, and `ApiResponse` envelope assertions. |
| `./gradlew allTest` | all three | Required final backend verification. |

The default `test` task runs `unit` only, keeping the edit loop fast.

## Coverage plan by domain

### Catalog

- **Controller:** public discovery validation, feedback submission, and admin authorization responses.
- **Service:** search correction, filtering, distance-sort preconditions, cursor validation, and lifecycle decisions.
- **Repository:** `StoreJpaRepository` published-status ordering and correction-rule lookup against PostgreSQL.
- **Integration flow:** create a draft, review and publish it, discover it publicly, then resolve feedback and record audit state.

### Identity and foundation

- **Controller/configuration:** health envelope and CORS origin normalization.
- **Authentication:** reusable test JWT fixtures for an `ADMIN` and a non-admin principal.
- **Contract:** unauthenticated and forbidden admin paths return the documented `ApiResponse` failure envelope.

## Shared test infrastructure

`PostgresIntegrationTest` owns one PostgreSQL 16 Testcontainer and supplies datasource properties dynamically. Tests reset only mutable rows, leaving Flyway seed data intact. Test authentication uses Spring Security's mock JWT request post-processors; no external OIDC provider is required.
