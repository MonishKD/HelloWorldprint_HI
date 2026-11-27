# Code metrics (Phase IV 5.2)

One folder per scanned git ref, made by [`scripts/sonar-scan.sh`](../../scripts/sonar-scan.sh). The exports are committed exactly as SonarQube returned them.

| Folder | Ref | Commit |
|---|---|---|
| `phase3` | `tags/phase3` (baseline, before any Phase IV change) | `7bc309f` |
| `phase4-refactored` | `tags/phase4-refactored` (after R1, R2 and R3, before RVW) | `87b7ed4` |
| `phase4` | `tags/phase4` (RVW added on top of `phase4-refactored`) | `f45c119` |

## Setup

- **SonarQube:** Community Build 26.11.0.134175, Docker image `sonarqube:26.11.0.134175-community`, run locally.
- **Scanner:** SonarScanner CLI 8.4.0.16527, Docker image `sonarsource/sonar-scanner-cli@sha256:6e6eca4d552695a98fb7dab84f0c1bd0885ebbfe272654d1552e24bd7f452d51`. It is pinned by digest because Docker Hub has no tag for this exact version. The image is amd64 only, so on Apple Silicon it runs under emulation, which is slower but gives the same results.
- **Quality profile:** the built-in default for each language, unchanged ("Sonar way core" for Java, TypeScript and CSS), with no custom rules.
- **What is scanned:** `app/backend/src/main/java` and `app/frontend/src`. Tests and `node_modules` are not scanned. The backend is compiled first, because the Java analysis reads the compiled classes.
- **Command** (from the repo root, with Docker running and Java 25 and `jq` installed):

  ```bash
  scripts/sonar-scan.sh tags/phase3 docs/metrics/phase3
  ```

  Use `tags/<name>`, not the bare name: `phase3` is also a branch name. Each ref gets its own SonarQube project (`fortunategoods-<name>`), so later scans don't overwrite earlier ones. SonarQube stays running at http://localhost:9000 (login `admin`, password `LocalScan_2026x` unless `SONAR_ADMIN_PASSWORD` is set). Stop it with `docker stop sonarqube-local`.

## Files

- `measures.json`: from `api/measures/component_tree`. Has `ncloc` (lines of code), `complexity` (cyclomatic), `cognitive_complexity` and `code_smells` for every source file.
- `code-smells.json`: from `api/issues/search?types=CODE_SMELL`, every open code smell, all pages merged into one list.
- `scan-info.json`: the ref, commit, SonarQube and scanner versions, quality profiles, command and date of the scan.

**A metric missing from a file means 0.** SonarQube leaves out measures whose value is 0, for example `code_smells` on a clean file or `cognitive_complexity` on a file with no branching.

## Baseline at `phase3`

| | Java | TypeScript | CSS | Total |
|---|---|---|---|---|
| Files | 204 | 43 | 15 | 262 |

Totals: 13,761 lines of code, cyclomatic complexity 2,555, cognitive complexity 940, 3 code smells (all `typescript:S9379`, `autoFocus`).

## After refactoring, at `phase4-refactored`

Same SonarQube version, scanner and quality profiles as `phase3` (`scan-info.json` is identical on those fields).

| | Java | TypeScript | CSS | Total |
|---|---|---|---|---|
| Files | 211 | 43 | 15 | 269 |

Totals: 13,933 lines of code, cyclomatic complexity 2,581, cognitive complexity 941, 3 code smells (the same three `autoFocus`).

## Comparison

Each cell is lines of code / cyclomatic complexity / cognitive complexity, from the two `measures.json` exports. No backend class in the table has a SonarQube code smell in any scan.

| Class | Refactoring | `phase3` | `phase4-refactored` | `phase4` |
|---|---|---|---|---|
| `OrderRepository` | R1 | 249 / 58 / 13 | 130 / 36 / 8 | 130 / 36 / 8 |
| `OrderQueryRepository` (new) | R1 | | 111 / 28 / 4 | 111 / 28 / 4 |
| `OrderHydrator` (new) | R1 | | 57 / 7 / 1 | 57 / 7 / 1 |
| `LedgerService` | R2 | 152 / 29 / 21 | 67 / 10 / 5 | 67 / 10 / 5 |
| `OrderHistoryService` (new) | R2 | | 114 / 22 / 16 | 114 / 22 / 16 |
| `DashboardService` | R3 | 107 / 15 / 12 | 31 / 5 / 0 | 31 / 5 / 0 |
| `CustomerDashboardQueryService` (new) | R3 | | 40 / 5 / 2 | 40 / 5 / 2 |
| `RestaurantDashboardQueryService` (new) | R3 | | 40 / 4 / 2 | 40 / 4 / 2 |
| `OperatorOrdersQueryService` (new) | R3 | | 52 / 8 / 8 | 52 / 8 / 8 |
| `CityViewQueryService` (new) | R3 | | 38 / 3 / 1 | 45 / 5 / 5 |
| `Order` | not refactored | 217 / 70 / 26 | 217 / 70 / 26 | 217 / 70 / 26 |
| `UserRepository` | not refactored | 128 / 29 / 11 | 128 / 29 / 11 | 128 / 29 / 11 |
| `RatingService` (RVW) | | | | 52 / 8 / 5 |
| `RatingRepository` (RVW) | | | | 37 / 7 / 1 |
| `RatingConfig` (RVW) | | | | 35 / 7 / 6 |
| `RatingController` (RVW) | | | | 35 / 3 / 0 |
| `RateOrderCommand` (RVW) | | | | 31 / 3 / 0 |
| `Rating` (RVW) | | | | 14 / 5 / 2 |
| `RvwModulePermissions` (RVW) | | | | 9 / 2 / 0 |
| `RatingScale` (RVW) | | | | 6 / 2 / 1 |
| `RateOrderReqJSON`, `RatingResponse`, `RestaurantRating` (RVW records) | | | | 2 / 0 / 0 each |
| `RateOrder.tsx` (RVW frontend) | | | | 170 / 46 / 22, 1 smell |
| `rvwApi.ts` (RVW frontend) | | | | 12 / 4 / 0 |

**What got worse.** Each hotspot shrank, but the classes it was split into add up to more than the original:

| Hotspot | `phase3` | Sum of its classes at `phase4-refactored` | Change |
|---|---|---|---|
| `OrderRepository` (R1) | 249 / 58 / 13 | 298 / 71 / 13 | +49 / +13 / 0 |
| `LedgerService` (R2) | 152 / 29 / 21 | 181 / 32 / 21 | +29 / +3 / 0 |
| `DashboardService` (R3) | 107 / 15 / 12 | 201 / 25 / 13 | +94 / +10 / +1 |

Most of the increase is structure rather than new logic. Each extracted class brings its own package line, imports, fields and constructor, which count as lines of code. SonarQube's cyclomatic complexity counts 1 for every method, so the new constructors and the delegating methods left in the original classes raise it too. Cognitive complexity, which only counts branching and nesting, is unchanged for R1 and R2, so their logic was moved rather than duplicated. R3 adds 1 because the private `restaurantName()` helper, which contains one `? :`, was copied into both `CustomerDashboardQueryService` and `OperatorOrdersQueryService`. The three rows above add up exactly to the change in the project totals (+172 / +26 / +1), so no other file changed. `Order` and `UserRepository` were not refactored, and their numbers are the same in both scans.

## RVW, at `phase4`

Same SonarQube version, scanner and quality profiles as the other two scans. 287 files (222 Java, 48 TypeScript, 17 CSS). Totals: 15,688 lines of code, cyclomatic complexity 2,963, cognitive complexity 1,158, 4 code smells. The three `autoFocus` smells are still there; the new one is `typescript:S6852` in `RateOrder.tsx` ("Elements with the 'radiogroup' interactive role must be focusable").

RVW reads its star range from the `ratings` block of `seed-config.json` (`minStars: 1`, `maxStars: 5`) through `RatingConfig`; the range is not written in the code.

RVW left every class touched by R1 and R2 unchanged, and of the R3 classes only `CityViewQueryService` changed (38 / 3 / 1 to 45 / 5 / 5), to add each restaurant's average rating to the city view; `DashboardService` itself is unchanged. The RVW backend adds 11 classes (225 lines, cyclomatic 37, cognitive 15). The existing backend classes it modified are `CityView`, `CityViewQueryService`, `CustomerOrderList`, `DashboardOrder`, `OrderExceptionHandler` (handles the `rvw` package) and `SeedLoader` (Reset also empties `ratings`).

From `phase4-refactored` to `phase4` the project grew by 1,755 lines: 253 in the backend and 1,502 in the frontend. Of the frontend lines, 1,162 are the party-mode files (`ui/party/`, `party.css`), which are not part of RVW and are left out of the comparison table.
