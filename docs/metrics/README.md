# Code metrics (Phase IV 5.2)

One folder per scanned git ref, made by [`scripts/sonar-scan.sh`](../../scripts/sonar-scan.sh). The exports are committed exactly as SonarQube returned them.

| Folder | Ref | Commit |
|---|---|---|
| `phase3` | `tags/phase3` (baseline, before any Phase IV change) | `7bc309f` |

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
