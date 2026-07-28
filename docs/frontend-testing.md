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
| UI integration | Discovery with mocked HTTP | initial store list, query-triggered requests, and loading failures. |
| Existing behavior | Navigation URL helpers and admin form constraints | Naver directions URL encoding, required category-flag fields. |

`pnpm --dir frontend test` runs all frontend tests. Root `ci:frontend` includes that command before lint, typecheck, and production build.
