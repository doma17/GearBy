# GearBy 통합 제품 스펙

상태: active / normative / current
대상 사용자: 한국의 아웃도어 활동 사용자
API 계약: [OpenAPI](../contracts/openapi.yaml)

## Source of Truth

이 문서와 아래 세 상세 문서가 GearBy 제품 요구사항의 유일한 Source of Truth다. 다른 문서, 코드, 이슈 또는 이전 Manyfast 자료가 이 문서군과 충돌하면 이 문서군을 기준으로 판단한다.

충돌 시 우선순위는 다음과 같다.

1. [`SPEC.md`](./SPEC.md) — 제품 기둥, 단계 경계, 공통 용어와 최상위 요구사항
2. [01. PRD](./spec/01.prd.md) — 사용자, 문제, P0/P1/P2 범위와 성공 기준
3. [02. 기능 명세](./spec/02.functional-spec.md) — 기능 동작, 입력·출력, 상태와 오류 계약
4. [03. 사용자 흐름](./spec/03.user-flow.md) — 사용자·관리자 완료 경로와 예외 흐름

하위 문서는 상위 문서를 상세화할 수 있지만 범위나 의미를 변경할 수 없다. 충돌이나 공백을 발견하면 임의로 해석하지 않고 상위 문서부터 결정한 뒤 하위 문서, [테스트 전략](./TESTING.md), [OpenAPI 계약](../contracts/openapi.yaml)과 구현을 순서대로 동기화한다. 테스트 전략과 OpenAPI는 이 문서군에서 파생된 검증·실행 계약이며 독립적인 제품 요구사항 원천이 아니다.

## 1. 제품 요구사항

### 제품 기둥
1. 지도 기반 검수 아웃도어 매장 발견
2. 활동·프로필 기반 장비군 가이드

### 단계
- **P0 first MVP — map discovery:** 대한민국 전역을 서비스 가능 지역으로 삼고, 지도/목록, 복수 매장 카테고리, 검색·필터, 상세, 길찾기, 정보 최신성·정정 흐름, 관리자 운영, 지도 UI의 접근 가능한 목록 대체를 제공한다. 전국의 모든 매장을 빠짐없이 보유한다는 의미는 아니며 검수·공개된 매장만 제공한다.
- **P1 planned — RecommendationSession gear-group guidance:** 활동 의도, 여행 프로필, 선호·제약을 받아 규칙 기반 장비군/스타터 키트와 이유, 경고, 미충족 제약, 검증된 매장 연결을 제공한다. P0 출시 이후 범위다.
- **P2 ProductRecommendation:** 카탈로그·출처·개인정보 결정 이후 별도 계약군으로 연기한다.

### P1 금지 범위
`RecommendationSession`은 장비군 안내만 다룬다. P1에서는 `Product`, `ProductVariant`, `price`, `availability`, `checkout` 필드와 구매 흐름을 만들지 않는다.

## 2. 기능 명세

### P0 map discovery
- 매장은 검수된 카테고리와 위치로 지도와 목록에 표시된다.
- 복수 카테고리는 하나 이상 일치하는 매장을 반환하는 OR 방식으로 결합한다.
- 검색어 보정 결과를 기본으로 표시하되 사용자가 원 검색어 결과로 되돌릴 수 있어야 한다.
- 지도 이동만으로 재검색하지 않고 사용자가 `이 지역 검색`을 선택하면 현재 지도 영역을 적용한다.
- 사용자는 상세에서 주소·영업시간·연락처·길찾기와 마지막 검증 시각을 확인한다.
- 검증 주기가 지난 매장은 `REVIEW_DUE`로 표시하고 관리자 재검수 대상으로 보내되 즉시 비공개하지 않는다.
- 크롤링과 NAVER 지도·검색 관련 API를 후보 수집 채널로 검증하고, 배치 수집한 후보를 중복 제거·검수한 뒤 공개한다. 공급자별 사용 가능 범위와 정책은 PoC에서 확정한다.
- 피드백은 로그인 없이 제출할 수 있으며 회신을 요청할 때만 이메일과 연락 동의를 함께 받는다.
- 공개 가입 없이 초대·승인된 관리자만 운영 기능을 사용하며, 관리자 UI와 API는 인증된 `ADMIN`에게만 노출·허용한다.
- 지도 기능은 모바일 웹과 웹에서 동작하며 목록 대체 경로를 제공한다.

### P1 RecommendationSession gear-group guidance
- 입력: `activity`, `trip profile`, `preferences`, `constraints`
- 입력값은 폐쇄적인 enum으로 제한하지 않고 검증 가능한 문자열과 확장 컨텍스트로 수용한다.
- API 출력: `ruleVersion`, `gear groups`, `starter-kit option`, `explanations`, `warnings`, `unmet constraints`, `verified store matches`
- `ruleVersion`은 계약 추적과 재현을 위해 API 응답에 포함하지만 일반 사용자 UI에는 노출하지 않는다.
- `Store match`는 `sourceType`, `sourceUrl`, `verifiedAt`, `expiresAt`, `verificationStatus`가 유효할 때만 노출한다.
- P1 출력에는 `Product`, `ProductVariant`, `price`, `availability`, `checkout`을 포함하지 않는다.

### P2 ProductRecommendation
- 제품/변형/매장 매칭과 선택적 랭킹은 P2에서 별도 계약군으로 다룬다.
- 출처와 확인 시각이 없는 실재고·상거래 주장은 P2에서도 허용하지 않는다.

## 3. 사용자 흐름

### P0 map discovery 흐름
1. 사용자가 홈/지도에 진입한다.
2. 카테고리 또는 검색어를 입력한다.
3. 시스템이 보정 가능한 검색어를 적용하고 지도와 목록을 동기화한다.
4. 사용자가 매장 마커나 목록 항목을 선택한다.
5. 상세에서 기본 정보, 전화, 외부 길찾기, 정정/피드백 경로를 확인한다.

### P1 RecommendationSession gear-group guidance 흐름
1. 사용자가 활동과 `trip profile`을 입력한다.
2. 사용자가 `preference`와 `constraint`를 확인한다.
3. 시스템이 API 응답의 `ruleVersion`으로 결과를 추적하고, 사용자에게는 `gear group` 또는 `starter kit`와 `explanation`, `warning`, `unmet constraint`만 보여준다.
4. 사용자는 검증된 `store match`로 이동하거나 `feedback`을 남긴다.
5. 이 흐름은 `Product`, `ProductVariant`, `price`, `availability`, `checkout`으로 이어지지 않는다.

### P2 ProductRecommendation 흐름
제품 수준 추천 흐름은 카탈로그와 출처 정책 확정 전까지 deferred 상태다.

## 4. P0 확정 정책과 PoC 경계

| 항목 | 확정 정책 |
| --- | --- |
| 복수 카테고리 | 하나 이상 일치하는 OR 방식 |
| 검색어 보정 | 보정 결과를 기본 표시하고 원 검색어 결과로 되돌리기 제공 |
| 외부 데이터 | 자동 공개하지 않고 `DRAFT → 관리자 검수 → PUBLISHED` 적용 |
| 관리자 계정 | 공개 가입 없이 초대·승인된 `ADMIN`만 허용 |
| 전국 범위 | 특정 지역을 코드로 제한하지 않으며 초기 매장 수 목표는 수집 PoC 이후 정함 |
| 피드백 | 익명 제출을 허용하고 회신 요청 시에만 이메일과 연락 동의를 함께 받음 |
| 지도 이동 | 자동 재검색하지 않고 `이 지역 검색`으로 현재 지도 영역 적용 |
| 정보 최신성 | 검증 주기가 지나면 `REVIEW_DUE`로 전환하되 즉시 비공개하지 않음 |

공급자별 허용 범위, 중복 판정 세부 규칙, 배치·재검증 주기, 실제 OIDC 공급자와 초기 데이터 목표치는 PoC 결과로 확정한다. 이는 위 제품 정책을 변경하지 않는 구현 매개변수다.

## 5. 용어

| 용어 | 정의 |
| --- | --- |
| Activity | 사용자가 준비하는 아웃도어 활동 의도. |
| Style preference | BPL/UL 지향, 가벼움, 설치 쉬움처럼 안전 등급이 아닌 선호. |
| Trip profile | 기간, 계절, 날씨, 인원, 이동수단, 무게 민감도 같은 외출 맥락. |
| Preference | 예산, 무게, 편의, 내구성 등 사용자의 가중치. |
| Constraint | 경험 수준, 계절, 제외 장비 같은 필수 조건. |
| Gear group | P1에서 사용하는 추상 장비 분류. |
| Store capability | `sourceType`, `sourceUrl`, `verifiedAt`, `expiresAt`, `verificationStatus`를 가진 매장 근거. |
| Store match | 검증되고 만료되지 않은 capability에 근거한 활동/장비군-매장 연결. |
| Recommendation explanation | 추천 이유, 제약, 주의 사항을 함께 설명한 문구. |
| RecommendationSession | P1 장비군 가이드 계약군. 제품·가격·재고·구매 정보를 포함하지 않는다. |
| ProductRecommendation | P2 이후 제품 수준 추천 계약군. 현재 deferred. |
| Product | P2 카탈로그 이후의 판매 가능 항목 개념. |
| ProductVariant | P2 카탈로그 이후의 특정 크기·색상·모델·판. |

## 6. 테스트 명세

이 절은 테스트 요구사항만 정의하며 실행 가능한 자동화 테스트를 추가하지 않는다.

### 원칙
- 이 문서군만 제품 요구사항으로 검증하고, 테스트 전략·OpenAPI·구현은 상위 스펙과의 동기화를 검증한다.
- P0 first MVP는 복수 카테고리, 검수된 매장 발견, 외부 후보 수집의 공개 차단, 지도/목록/상세/길찾기 동등성, 피드백과 관리자 인가를 출시 기준으로 검증한다.
- P1 planned 테스트 ID는 다음 단계의 계약을 고정하지만 P0 출시 게이트에는 포함하지 않는다.
- P1에서는 `Product`, `ProductVariant`, `price`, `availability`, `checkout`을 검증하지 않는다.
- P2 `ProductRecommendation`은 deferred 상태이므로 자동화 대상이 아니다.

### 테스트 ID

| ID | 계층 | 검증 대상 |
| --- | --- | --- |
| DOC-01 | 문서 | `SPEC.md`와 세 상세 문서만 제품 요구사항의 Source of Truth이며 파생 문서와 구현은 그 우선순위를 침범하지 않는다. |
| DOC-02 | 문서 | P0 map discovery는 first MVP, P1 RecommendationSession은 planned, P2 ProductRecommendation은 deferred로 구분된다. |
| API-CUR-01 | API 계약 | `GET /stores`, `GET /stores/{storeId}`가 OR 복수 카테고리, 보정 적용 여부, 지도 영역과 정보 최신성을 포함한 조회 경계를 제공한다. |
| API-REC-01 | API 계약 | RecommendationSession 5개 엔드포인트가 `ApiResponse` envelope와 `ruleVersion`을 포함한 필수 profile/guidance/store-match/feedback shape을 지킨다. |
| DOM-REC-01 | 도메인 | activity/trip profile/preference/constraint를 결정적으로 정규화하고 gear group, explanation, warning, unmet constraint를 산출한다. |
| INT-CUR-01 | 통합 | 동일 필터가 지도와 목록에 같은 검수 매장 집합을 제공하고 원 검색어 재검색과 상세로 이어지며 `REVIEW_DUE` 매장은 공개 상태를 유지한다. |
| INT-CUR-02 | 통합 | 외부 수집 후보가 중복 제거와 검수를 거치며 `PUBLISHED` 전에는 공개 검색에 노출되지 않는다. |
| INT-REC-01 | 통합 | guidance 결과의 gear group이 검증되고 만료되지 않은 store match 조회로 이어진다. |
| UI-CUR-01 | UI | 지도, `이 지역 검색`, 목록, 검색 보정 되돌리기, 최신성 표시, 상세, 길찾기와 목록 대체 경로가 접근 가능하게 동작한다. |
| UI-OPS-01 | UI·보안 | 관리자 진입점과 화면은 `ADMIN` 세션에서만 보이고 관리자 API는 미인증·권한 부족 요청을 거부한다. |
| UI-REC-01 | UI | onboarding이 최소 profile을 수집하고 결과 화면이 이유, 경고, 미충족 제약, store match를 보여준다. |
| E2E-CUR-01 | E2E | 사용자가 OR 카테고리·검색 보정·지도 영역 검색에서 매장 상세와 외부 길찾기까지 완료한다. |
| E2E-REC-01 | E2E | 사용자가 profile 입력 후 장비군 안내를 보고 검증된 매장 발견 흐름으로 이동한다. |

### 제외
- 이 문서는 runnable backend/frontend/unit/e2e 테스트 파일을 만들지 않는다.
- P1에서 실재고, 가격, 결제, 제품 변형, 구매 가능성 주장은 실패 조건이다.
- ProductRecommendation 자동화는 카탈로그, 출처, 개인정보 정책이 확정된 뒤 별도 명세로 추가한다.

## 7. 요구사항 추적성

### 매트릭스

| 우선순위 | 요구사항 | 흐름 | 계약 경계 | 테스트 ID |
| --- | --- | --- | --- | --- |
| P0 first MVP | CUR-010: 전국 범위 검수 매장 발견, 복수 카테고리, 지도/목록/상세/길찾기, 피드백·관리자 운영 | FLOW-CUR-010, FLOW-FDB-010, FLOW-OPS-010 | 기존 store·feedback·admin API | DOC-01, API-CUR-01, INT-CUR-01, INT-CUR-02, UI-CUR-01, UI-OPS-01, E2E-CUR-01 |
| P1 planned | REC-010: activity/trip profile 기반 장비군 안내와 검증 매장 연결 | FLOW-REC-010: onboarding → result/reasons → map match | RecommendationSession 5개 엔드포인트 | DOC-02, API-REC-01, DOM-REC-01, INT-REC-01, UI-REC-01, E2E-REC-01 (planned) |
| P2 | ProductRecommendation | deferred | Product/ProductVariant/price/availability/checkout 계약군 없음 | deferred |

### 역추적

| 테스트 ID | 연결 |
| --- | --- |
| DOC-01 | P0/P1 공통 Source of Truth 체계와 파생 문서 우선순위 |
| DOC-02 | P1 범위와 P2 deferred 경계 |
| API-CUR-01 | CUR-010, FLOW-CUR-010, `GET /stores`, `GET /stores/{storeId}` |
| API-REC-01 | REC-010, FLOW-REC-010, RecommendationSession 5개 엔드포인트 |
| DOM-REC-01 | REC-010의 결정적 장비군 규칙과 설명 가능성 |
| INT-CUR-01 | CUR-010의 필터, 지도/목록 동기화, 상세 연결 |
| INT-CUR-02 | CUR-010의 외부 후보 수집, 중복 제거, 검수 후 공개 경계 |
| INT-REC-01 | REC-010의 guidance → verified store match 연결 |
| UI-CUR-01 | FLOW-CUR-010의 접근 가능한 지도/목록/상세 UI |
| UI-OPS-01 | FLOW-OPS-010의 역할 기반 관리자 UI 노출과 API 인가 |
| UI-REC-01 | FLOW-REC-010의 onboarding/result/store-match UI |
| E2E-CUR-01 | FLOW-CUR-010 사용자 완료 경로 |
| E2E-REC-01 | FLOW-REC-010 사용자 완료 경로 |

### 단계 경계
- P0 CUR-010은 store·feedback·admin API와 외부 후보 수집의 검수 경계를 사용한다.
- P1 REC-010/FLOW-REC-010은 P0 출시 이후 planned이며 RecommendationSession 5개 엔드포인트를 모두 사용한다.
- P2 ProductRecommendation은 deferred이며 현재 테스트 ID를 배정하지 않는다.
