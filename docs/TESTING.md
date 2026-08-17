# GearBy test strategy

이 문서는 [`SPEC.md`](./SPEC.md)와 [`docs/spec`](./spec)의 요구사항을 검증하기 위한 파생 문서다. 제품 요구사항의 Source of Truth는 아니며, 충돌하면 스펙 문서군을 먼저 명확히 한 뒤 테스트를 동기화한다.

## P0 first MVP release gates

- 반복 `category` 쿼리로 복수 카테고리를 전달하고, OR 규칙으로 지도와 목록이 같은 결과를 반환해야 한다.
- 검색 보정 결과에서 `applyCorrection=false`로 원 검색어 결과를 다시 조회할 수 있어야 한다.
- 지도 이동만으로 요청하지 않고 `이 지역 검색`을 선택했을 때 현재 `bbox`로 지도와 목록을 함께 갱신해야 한다.
- 외부 수집 후보는 중복 판정과 관리자 검수를 통과하기 전 공개 API에 노출되지 않아야 하며, 후보 해결로 생성된 `DRAFT`는 관리자 `IN_REVIEW → PUBLISHED` 전환 뒤에만 탐색되어야 한다.
- 재검증 주기가 지난 공개 매장은 `REVIEW_DUE`가 되지만 자동으로 비공개되지 않아야 한다.
- 피드백은 익명으로 제출할 수 있고 회신 이메일은 연락 동의와 함께 검증되어야 한다.
- 관리자 API는 토큰이 없으면 `401`, `ADMIN` 권한이 없으면 `403`을 반환해야 한다.
- 관리자 진입점과 운영 화면은 검증된 `ADMIN` 세션에서만 렌더링되어야 한다.
- P1 추천 테스트는 planned 계약 검증이며 P0 출시 게이트에 포함하지 않는다.

관리자 UI는 서버 세션 로그인과 CSRF 요청 헤더를 사용한다. 후보 검수 UI는 `QUARANTINED` 후보만 표시하며, 기존 매장 연결 또는 `DRAFT` 생성 요청을 생성된 OpenAPI 타입과 `ApiResponse` 파서로 처리한다. 실제 OIDC 연동은 P0 이후 범위다.

## Backend

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
- **Integration flow:** resolve a quarantined candidate to `DRAFT`, review and publish it through the administrator API, discover it publicly, then resolve feedback and record audit state.

### Identity and foundation

- **Controller/configuration:** health envelope and CORS origin normalization.
- **Authentication:** reusable test JWT fixtures for an `ADMIN` and a non-admin principal.
- **Contract:** unauthenticated and forbidden admin paths return the documented `ApiResponse` failure envelope.

## Shared test infrastructure

`PostgresIntegrationTest` owns one PostgreSQL 16 Testcontainer and supplies datasource properties dynamically. Tests reset only mutable rows, leaving Flyway seed data intact. Test authentication uses Spring Security's mock JWT request post-processors; no external OIDC provider is required.

# Frontend state and testing strategy

## State boundary

Discovery keeps server data (`categories`, `stores`, `detail`) close to its fetch effects. User-controlled view state moves to a reducer: query, selected categories, sort, location, selected store, active panel, and visible errors. This avoids a global store for state that belongs to one screen.

Admin token and operations data remain local to the admin screen; they do not need to be shared with discovery.

## TDD loop

1. Add a failing reducer or component test for the user-visible behavior.
2. Make the smallest state transition or UI change that passes it.
3. Refactor only after `test`, lint, typecheck, and build remain green.

## Test layers

| Layer | Scope | Examples |
| --- | --- | --- |
| Unit | Pure reducer and API-envelope helpers | category toggling, search state transitions, API failures. |
| UI integration | Discovery and administrator views with mocked HTTP | initial store list, query-triggered requests, candidate resolution payloads, focus restoration, and candidate failure isolation. |
| Existing behavior | Navigation URL helpers and admin form constraints | Naver directions URL encoding and required category-flag fields. |

`pnpm --dir frontend test` runs all frontend tests. Root `ci:frontend` includes that command before lint, typecheck, and production build.

### P0 public journey evidence

`discovery.test.tsx` verifies one mocked-HTTP user path: OR category filters → store detail → NAVER map link → anonymous correction feedback. It is UI integration evidence, not a live NAVER or deployed-browser test.
